/*
 * 灵犀 Lingxi
 * 参考自 Operit AI (https://github.com/AAswordman/Operit) 的蓝牙工具
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.tools

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "BluetoothTools"
private val DEFAULT_SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

private data class ClassicSession(
    val socket: BluetoothSocket,
    val input: InputStream,
    val output: OutputStream,
    val address: String,
)

private data class BleSession(
    val gatt: BluetoothGatt,
    val address: String,
    val servicesReady: CompletableDeferred<Boolean>,
    val notifications: MutableList<Pair<String, String>>,
)

private val classicSessions = ConcurrentHashMap<String, ClassicSession>()
private val bleSessions = ConcurrentHashMap<String, BleSession>()

private fun adapter(context: Context): BluetoothAdapter? =
    (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

private fun newSessionId(prefix: String): String = "$prefix-${UUID.randomUUID()}"

@SuppressLint("MissingPermission")
fun createBluetoothTools(context: Context): List<Tool> = listOf(
    Tool(
        name = "bluetooth_scan",
        description = "Scan for nearby Bluetooth devices (classic + BLE). Returns name, address, type, RSSI.",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("duration_ms", buildJsonObject {
                        put("type", "integer")
                        put("description", "Scan duration in ms (default 10000)")
                    })
                }
            )
        },
        execute = { input ->
            val dur = input.jsonObject["duration_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 10000L
            val ad = adapter(context) ?: return@Tool listOf(UIMessagePart.Text("""{"error":"Bluetooth not supported"}"""))
            val results = ConcurrentHashMap<String, String>()
            coroutineScope {
                val job = launch(Dispatchers.Main) {
                    val sc = ad.bluetoothLeScanner
                    val cb = object : ScanCallback() {
                        override fun onScanResult(callbackType: Int, r: ScanResult) {
                            val d = r.device
                            results[d.address] = "${d.name ?: ""}|${d.address}|le|rssi=${r.rssi}"
                        }
                    }
                    try {
                        sc?.startScan(cb)
                        delay(dur)
                        sc?.stopScan(cb)
                    } catch (e: Exception) {
                        Log.w(TAG, "BLE scan: ${e.message}")
                    }
                }
                job.join()
            }
            listOf(UIMessagePart.Text(buildJsonObject {
                put("count", results.size)
                put("devices", JsonPrimitive(results.values.joinToString("\n")))
            }.toString()))
        }
    ),
    Tool(
        name = "bluetooth_list_bonded_devices",
        description = "List all bonded/paired Bluetooth devices.",
        needsApproval = false,
        parameters = { InputSchema.Obj(properties = buildJsonObject { }) },
        execute = {
            val ad = adapter(context) ?: return@Tool listOf(UIMessagePart.Text("""{"error":"Bluetooth not supported"}"""))
            val devs = ad.bondedDevices.map { "${it.name ?: ""}|${it.address}" }
            listOf(UIMessagePart.Text(buildJsonObject {
                put("count", devs.size)
                put("devices", JsonPrimitive(devs.joinToString("\n")))
            }.toString()))
        }
    ),
    Tool(
        name = "bluetooth_connect",
        description = "Connect to a classic Bluetooth device by address using SPP (RFCOMM). Returns a session_id.",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("address", buildJsonObject {
                        put("type", "string")
                        put("description", "Device MAC address")
                    })
                },
                required = listOf("address"),
            )
        },
        execute = { input ->
            val addr = input.jsonObject["address"]?.jsonPrimitive?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("""{"error":"address required"}"""))
            val ad = adapter(context) ?: return@Tool listOf(UIMessagePart.Text("""{"error":"Bluetooth not supported"}"""))
            val dev = ad.getRemoteDevice(addr)
            val sock = dev.createRfcommSocketToServiceRecord(DEFAULT_SPP_UUID)
            withContext(Dispatchers.IO) { sock.connect() }
            val sid = newSessionId("classic")
            classicSessions[sid] = ClassicSession(sock, sock.inputStream, sock.outputStream, addr)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("session_id", sid)
                put("address", addr)
                put("connected", true)
            }.toString()))
        }
    ),
    Tool(
        name = "bluetooth_send",
        description = "Send data over a classic Bluetooth session. Returns bytes sent.",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("session_id", buildJsonObject {
                        put("type", "string")
                        put("description", "Session id from bluetooth_connect")
                    })
                    put("data", buildJsonObject {
                        put("type", "string")
                        put("description", "Data to send")
                    })
                },
                required = listOf("session_id", "data"),
            )
        },
        execute = { input ->
            val sid = input.jsonObject["session_id"]?.jsonPrimitive?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("""{"error":"session_id required"}"""))
            val data = input.jsonObject["data"]?.jsonPrimitive?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("""{"error":"data required"}"""))
            val s = classicSessions[sid] ?: return@Tool listOf(UIMessagePart.Text("""{"error":"session not found"}"""))
            withContext(Dispatchers.IO) { s.output.write(data.toByteArray()); s.output.flush() }
            listOf(UIMessagePart.Text(buildJsonObject { put("sent", data.length) }.toString()))
        }
    ),
    Tool(
        name = "bluetooth_read",
        description = "Read available data from a classic Bluetooth session.",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("session_id", buildJsonObject { put("type", "string") })
                    put("timeout_ms", buildJsonObject {
                        put("type", "integer")
                        put("description", "Read timeout in ms (default 3000)")
                    })
                },
                required = listOf("session_id"),
            )
        },
        execute = { input ->
            val sid = input.jsonObject["session_id"]?.jsonPrimitive?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("""{"error":"session_id required"}"""))
            val to = input.jsonObject["timeout_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3000L
            val s = classicSessions[sid] ?: return@Tool listOf(UIMessagePart.Text("""{"error":"session not found"}"""))
            val buf = withContext(Dispatchers.IO) {
                val end = System.currentTimeMillis() + to
                val sb = StringBuilder()
                while (System.currentTimeMillis() < end) {
                    while (s.input.available() > 0) { sb.append(s.input.read().toChar()) }
                    delay(50)
                }
                sb.toString()
            }
            listOf(UIMessagePart.Text(buildJsonObject { put("data", buf) }.toString()))
        }
    ),
    Tool(
        name = "bluetooth_close",
        description = "Close a Bluetooth session (classic or BLE).",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("session_id", buildJsonObject { put("type", "string") })
                },
                required = listOf("session_id"),
            )
        },
        execute = { input ->
            val sid = input.jsonObject["session_id"]?.jsonPrimitive?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("""{"error":"session_id required"}"""))
            classicSessions.remove(sid)?.socket?.close()
            bleSessions.remove(sid)?.gatt?.close()
            listOf(UIMessagePart.Text(buildJsonObject { put("closed", sid) }.toString()))
        }
    ),
    Tool(
        name = "bluetooth_ble_connect",
        description = "Connect to a BLE device by address. Returns a session_id for BLE operations.",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("address", buildJsonObject {
                        put("type", "string")
                        put("description", "Device MAC address")
                    })
                },
                required = listOf("address"),
            )
        },
        execute = { input ->
            val addr = input.jsonObject["address"]?.jsonPrimitive?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("""{"error":"address required"}"""))
            val ad = adapter(context) ?: return@Tool listOf(UIMessagePart.Text("""{"error":"Bluetooth not supported"}"""))
            val dev = ad.getRemoteDevice(addr)
            val ready = CompletableDeferred<Boolean>()
            val notifs = mutableListOf<Pair<String, String>>()
            val gatt = withContext(Dispatchers.Main) {
                dev.connectGatt(context, false, object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                        if (newState == 2) ready.complete(true)
                    }
                    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) { ready.complete(true) }
                    override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
                        notifs.add(c.uuid.toString() to String(c.value))
                    }
                })
            }
            withTimeoutOrNull(15000) { gatt.connect(); ready.await() }
            val sid = newSessionId("ble")
            bleSessions[sid] = BleSession(gatt, addr, ready, notifs)
            listOf(UIMessagePart.Text(buildJsonObject {
                put("session_id", sid)
                put("address", addr)
                put("connected", true)
            }.toString()))
        }
    ),
    Tool(
        name = "bluetooth_ble_discover_services",
        description = "Discover BLE services and characteristics of a connected BLE device.",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("session_id", buildJsonObject { put("type", "string") })
                },
                required = listOf("session_id"),
            )
        },
        execute = { input ->
            val sid = input.jsonObject["session_id"]?.jsonPrimitive?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("""{"error":"session_id required"}"""))
            val s = bleSessions[sid] ?: return@Tool listOf(UIMessagePart.Text("""{"error":"BLE session not found"}"""))
            withTimeoutOrNull(10000) { s.gatt.discoverServices(); s.servicesReady.await() }
            val svcs = s.gatt.services?.map { svc ->
                "${svc.uuid}:" + svc.characteristics?.joinToString(",") { it.uuid.toString() }
            }?.joinToString("\n") ?: ""
            listOf(UIMessagePart.Text(buildJsonObject { put("services", JsonPrimitive(svcs)) }.toString()))
        }
    ),
    Tool(
        name = "bluetooth_ble_read_characteristic",
        description = "Read a BLE characteristic value by UUID.",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("session_id", buildJsonObject { put("type", "string") })
                    put("service_uuid", buildJsonObject { put("type", "string") })
                    put("characteristic_uuid", buildJsonObject { put("type", "string") })
                },
                required = listOf("session_id", "service_uuid", "characteristic_uuid"),
            )
        },
        execute = { input ->
            val sid = input.jsonObject["session_id"]?.jsonPrimitive?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("""{"error":"session_id required"}"""))
            val suuid = input.jsonObject["service_uuid"]?.jsonPrimitive?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("""{"error":"service_uuid required"}"""))
            val cuuid = input.jsonObject["characteristic_uuid"]?.jsonPrimitive?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("""{"error":"characteristic_uuid required"}"""))
            val s = bleSessions[sid] ?: return@Tool listOf(UIMessagePart.Text("""{"error":"BLE session not found"}"""))
            val svc = s.gatt.getService(UUID.fromString(suuid)) ?: return@Tool listOf(UIMessagePart.Text("""{"error":"service not found"}"""))
            val ch = svc.getCharacteristic(UUID.fromString(cuuid)) ?: return@Tool listOf(UIMessagePart.Text("""{"error":"characteristic not found"}"""))
            withContext(Dispatchers.Main) { s.gatt.readCharacteristic(ch) }
            delay(500)
            val v = ch.value
            listOf(UIMessagePart.Text(buildJsonObject {
                put("success", true)
                put("value", JsonPrimitive(String(v)))
            }.toString()))
        }
    ),
    Tool(
        name = "bluetooth_ble_write_characteristic",
        description = "Write data to a BLE characteristic by UUID.",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("session_id", buildJsonObject { put("type", "string") })
                    put("service_uuid", buildJsonObject { put("type", "string") })
                    put("characteristic_uuid", buildJsonObject { put("type", "string") })
                    put("data", buildJsonObject {
                        put("type", "string")
                        put("description", "Data to write (UTF-8 string)")
                    })
                },
                required = listOf("session_id", "service_uuid", "characteristic_uuid", "data"),
            )
        },
        execute = { input ->
            val sid = input.jsonObject["session_id"]?.jsonPrimitive?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("""{"error":"session_id required"}"""))
            val suuid = input.jsonObject["service_uuid"]?.jsonPrimitive?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("""{"error":"service_uuid required"}"""))
            val cuuid = input.jsonObject["characteristic_uuid"]?.jsonPrimitive?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("""{"error":"characteristic_uuid required"}"""))
            val data = input.jsonObject["data"]?.jsonPrimitive?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("""{"error":"data required"}"""))
            val s = bleSessions[sid] ?: return@Tool listOf(UIMessagePart.Text("""{"error":"BLE session not found"}"""))
            val svc = s.gatt.getService(UUID.fromString(suuid)) ?: return@Tool listOf(UIMessagePart.Text("""{"error":"service not found"}"""))
            val ch = svc.getCharacteristic(UUID.fromString(cuuid)) ?: return@Tool listOf(UIMessagePart.Text("""{"error":"characteristic not found"}"""))
            ch.value = data.toByteArray()
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            withContext(Dispatchers.Main) { s.gatt.writeCharacteristic(ch) }
            listOf(UIMessagePart.Text(buildJsonObject { put("success", true) }.toString()))
        }
    ),
    Tool(
        name = "bluetooth_ble_subscribe_characteristic",
        description = "Subscribe to BLE characteristic notifications. Use bluetooth_ble_read_notifications to read received data.",
        needsApproval = true,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("session_id", buildJsonObject { put("type", "string") })
                    put("service_uuid", buildJsonObject { put("type", "string") })
                    put("characteristic_uuid", buildJsonObject { put("type", "string") })
                },
                required = listOf("session_id", "service_uuid", "characteristic_uuid"),
            )
        },
        execute = { input ->
            val sid = input.jsonObject["session_id"]?.jsonPrimitive?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("""{"error":"session_id required"}"""))
            val suuid = input.jsonObject["service_uuid"]?.jsonPrimitive?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("""{"error":"service_uuid required"}"""))
            val cuuid = input.jsonObject["characteristic_uuid"]?.jsonPrimitive?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("""{"error":"characteristic_uuid required"}"""))
            val s = bleSessions[sid] ?: return@Tool listOf(UIMessagePart.Text("""{"error":"BLE session not found"}"""))
            val svc = s.gatt.getService(UUID.fromString(suuid)) ?: return@Tool listOf(UIMessagePart.Text("""{"error":"service not found"}"""))
            val ch = svc.getCharacteristic(UUID.fromString(cuuid)) ?: return@Tool listOf(UIMessagePart.Text("""{"error":"characteristic not found"}"""))
            withContext(Dispatchers.Main) { s.gatt.setCharacteristicNotification(ch) }
            val d = ch.getDescriptor(CCCD_UUID)
            if (d != null) {
                d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                s.gatt.writeDescriptor(d)
            }
            listOf(UIMessagePart.Text(buildJsonObject { put("subscribed", true) }.toString()))
        }
    ),
    Tool(
        name = "bluetooth_ble_read_notifications",
        description = "Read all cached BLE notification data received since last read. Clears the cache.",
        needsApproval = false,
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("session_id", buildJsonObject { put("type", "string") })
                },
                required = listOf("session_id"),
            )
        },
        execute = { input ->
            val sid = input.jsonObject["session_id"]?.jsonPrimitive?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("""{"error":"session_id required"}"""))
            val s = bleSessions[sid] ?: return@Tool listOf(UIMessagePart.Text("""{"error":"BLE session not found"}"""))
            val notifs = s.notifications.toList()
            s.notifications.clear()
            listOf(UIMessagePart.Text(buildJsonObject {
                put("count", notifs.size)
                put("data", JsonPrimitive(notifs.joinToString("\n") { "${it.first}: ${it.second}" }))
            }.toString()))
        }
    ),
)