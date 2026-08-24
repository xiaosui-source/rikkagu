/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/scottwilliamavery26071994-bot/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.ui.pages.setting

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dokar.sonner.ToastType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.Camera01
import me.rerere.rikkahub.data.game.GomokuGame
import me.rerere.rikkahub.data.game.MoveResult
import me.rerere.rikkahub.ui.context.LocalToaster

/** 模式枚举 */
private enum class GomokuMode { PLAY, ENDGAME }

/** 摆放棋子类型 */
private enum class PlacePiece { BLACK, WHITE, ERASER }

@Composable
fun SettingGomokuPage(onBack: () -> Unit = {}) {
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 棋盘状态
    val board = remember {
        Array(GomokuGame.BOARD_SIZE) { IntArray(GomokuGame.BOARD_SIZE) { GomokuGame.EMPTY } }
    }
    var currentPlayer by remember { mutableStateOf(GomokuGame.BLACK) }
    var winner by remember { mutableStateOf(0) }
    var statusText by remember { mutableStateOf("轮到你下棋（黑子）") }
    var isAIThinking by remember { mutableStateOf(false) }

    // 模式
    var mode by remember { mutableStateOf(GomokuMode.PLAY) }

    // 残局模式
    var placePiece by remember { mutableStateOf(PlacePiece.BLACK) }
    var solveResults by remember { mutableStateOf<List<MoveResult>>(emptyList()) }
    var highlightedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var solvedPlayer by remember { mutableStateOf(GomokuGame.BLACK) }

    fun resetGame() {
        for (x in board.indices) for (y in board.indices) board[x][y] = GomokuGame.EMPTY
        currentPlayer = GomokuGame.BLACK
        winner = 0
        statusText = "轮到你下棋（黑子）"
        isAIThinking = false
        solveResults = emptyList()
        highlightedCell = null
    }

    fun aiMove() {
        if (winner != 0) return
        val best = GomokuGame.getBestMove(board, GomokuGame.WHITE) ?: return
        board[best.first][best.second] = GomokuGame.WHITE
        if (GomokuGame.checkWin(board, GomokuGame.WHITE, best.first, best.second)) {
            winner = GomokuGame.WHITE
            statusText = "AI 获胜！"
        } else if (GomokuGame.isBoardFull(board)) {
            winner = -1
            statusText = "平局"
        } else {
            currentPlayer = GomokuGame.BLACK
            statusText = "轮到你下棋（黑子）"
        }
        isAIThinking = false
    }

    fun playerMove(x: Int, y: Int) {
        if (mode == GomokuMode.PLAY) {
            if (winner != 0 || currentPlayer != GomokuGame.BLACK || isAIThinking) return
            if (board[x][y] != GomokuGame.EMPTY) return
            board[x][y] = GomokuGame.BLACK
            if (GomokuGame.checkWin(board, GomokuGame.BLACK, x, y)) {
                winner = GomokuGame.BLACK
                statusText = "你赢了！"
                return
            }
            if (GomokuGame.isBoardFull(board)) {
                winner = -1
                statusText = "平局"
                return
            }
            currentPlayer = GomokuGame.WHITE
            statusText = "AI 思考中..."
            isAIThinking = true
            scope.launch {
                delay(300)
                aiMove()
            }
        } else {
            // 残局模式：摆放棋子
            if (board[x][y] == GomokuGame.EMPTY && placePiece == PlacePiece.ERASER) return
            board[x][y] = when (placePiece) {
                PlacePiece.BLACK -> GomokuGame.BLACK
                PlacePiece.WHITE -> GomokuGame.WHITE
                PlacePiece.ERASER -> GomokuGame.EMPTY
            }
            solveResults = emptyList()
            highlightedCell = null
            // 更新状态
            val existing = GomokuGame.checkAnyWin(board)
            statusText = if (existing != 0) {
                val name = if (existing == GomokuGame.BLACK) "黑子" else "白子"
                "当前局面：$name 已获胜"
            } else {
                val blackCount = board.sumOf { row -> row.count { it == GomokuGame.BLACK } }
                val whiteCount = board.sumOf { row -> row.count { it == GomokuGame.WHITE } }
                "编辑残局 · 黑${blackCount}子 白${whiteCount}子"
            }
        }
    }

    fun solveEndgame() {
        val existing = GomokuGame.checkAnyWin(board)
        if (existing != 0) {
            val name = if (existing == GomokuGame.BLACK) "黑子" else "白子"
            toaster.show("当前局面 $name 已获胜，无需求解", type = ToastType.Info)
            return
        }
        val hasPieces = board.any { row -> row.any { it != GomokuGame.EMPTY } }
        if (!hasPieces) {
            toaster.show("请先在棋盘上摆放棋子", type = ToastType.Warning)
            return
        }
        solveResults = GomokuGame.solveEndgame(board, solvedPlayer, topN = 5)
        highlightedCell = null
        if (solveResults.isNotEmpty()) {
            statusText = "找到 ${solveResults.size} 个候选落子（${if (solvedPlayer == GomokuGame.BLACK) "黑" else "白"}子）"
        } else {
            toaster.show("棋盘已满，无法求解", type = ToastType.Warning)
        }
    }

    // 拍照识别
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap == null) return@rememberLauncherForActivityResult
        scope.launch {
            statusText = "识别中..."
            val result = withContext(Dispatchers.Default) {
                GomokuGame.recognizeBoard(bitmap)
            }
            // 将识别结果填入棋盘
            for (x in 0 until GomokuGame.BOARD_SIZE) {
                for (y in 0 until GomokuGame.BOARD_SIZE) {
                    board[x][y] = result.board[x][y]
                }
            }
            solveResults = emptyList()
            highlightedCell = null
            val blackCount = board.sumOf { row -> row.count { it == GomokuGame.BLACK } }
            val whiteCount = board.sumOf { row -> row.count { it == GomokuGame.WHITE } }
            val confidencePct = (result.confidence * 100).toInt()
            statusText = "识别完成 · 黑${blackCount}子 白${whiteCount}子 · 置信度${confidencePct}%"
            if (confidencePct < 50) {
                toaster.show("识别置信度较低，请确保光线充足、棋盘清晰", type = ToastType.Warning)
            } else {
                toaster.show("棋盘识别完成", type = ToastType.Success)
            }
        }
    }

    // 相机权限请求（没有权限时先申请，避免直接打开相机崩溃）
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraLauncher.launch(null)
        } else {
            toaster.show("需要相机权限才能拍照识别棋盘，请在系统设置中开启", type = ToastType.Error)
        }
    }

    fun openCameraSafely() {
        val hasCamera = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCamera) {
            cameraLauncher.launch(null)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("五子棋") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(HugeIcons.ArrowLeft01, contentDescription = "返回")
                    }
                },
                actions = {
                    // 拍照按钮
                    if (mode == GomokuMode.ENDGAME) {
                        IconButton(onClick = { openCameraSafely() }) {
                            Icon(HugeIcons.Camera01, contentDescription = "拍照识别")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 模式切换
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                GomokuMode.entries.forEachIndexed { index, m ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index, GomokuMode.entries.size),
                        selected = mode == m,
                        onClick = {
                            mode = m
                            resetGame()
                        },
                        label = {
                            Text(if (m == GomokuMode.PLAY) "对战模式" else "残局破解")
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 状态文字
            Text(statusText, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            // 残局模式工具栏
            if (mode == GomokuMode.ENDGAME) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("摆放：", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(
                        onClick = { placePiece = PlacePiece.BLACK },
                        colors = if (placePiece == PlacePiece.BLACK)
                            ButtonDefaults.outlinedButtonColors(contentColor = Color.Black)
                        else ButtonDefaults.outlinedButtonColors(),
                    ) { Text("● 黑子") }
                    OutlinedButton(
                        onClick = { placePiece = PlacePiece.WHITE },
                        colors = if (placePiece == PlacePiece.WHITE)
                            ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF666666))
                        else ButtonDefaults.outlinedButtonColors(),
                    ) { Text("○ 白子") }
                    OutlinedButton(
                        onClick = { placePiece = PlacePiece.ERASER },
                    ) { Text("🧹 擦除") }
                }
                Spacer(Modifier.height(4.dp))

                // 求解方选择 + 求解按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("求解方：", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(
                        onClick = { solvedPlayer = GomokuGame.BLACK },
                        colors = if (solvedPlayer == GomokuGame.BLACK)
                            ButtonDefaults.outlinedButtonColors(contentColor = Color.Black)
                        else ButtonDefaults.outlinedButtonColors(),
                    ) { Text("黑子") }
                    OutlinedButton(
                        onClick = { solvedPlayer = GomokuGame.WHITE },
                        colors = if (solvedPlayer == GomokuGame.WHITE)
                            ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF666666))
                        else ButtonDefaults.outlinedButtonColors(),
                    ) { Text("白子") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { solveEndgame() }) {
                        Text("求解")
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // 棋盘
            val cellSize = 22.dp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFDEB887))
                    .padding(4.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                for (y in 0 until GomokuGame.BOARD_SIZE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        for (x in 0 until GomokuGame.BOARD_SIZE) {
                            val piece = board[x][y]
                            val isHighlighted = highlightedCell?.let { it.first == x && it.second == y } == true

                            Box(
                                modifier = Modifier
                                    .size(cellSize)
                                    .clickable(
                                        enabled = if (mode == GomokuMode.PLAY)
                                            winner == 0 && !isAIThinking
                                        else true
                                    ) { playerMove(x, y) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Canvas(Modifier.fillMaxSize()) {
                                    val gridColor = Color(0xFF8B7355)
                                    val cx = size.width / 2
                                    val cy = size.height / 2
                                    // 网格线
                                    drawLine(gridColor, Offset(0f, cy), Offset(size.width, cy), 1.dp.toPx())
                                    drawLine(gridColor, Offset(cx, 0f), Offset(cx, size.height), 1.dp.toPx())

                                    if (piece == GomokuGame.BLACK) {
                                        drawCircle(Color(0xFF1A1A1A), radius = size.width * 0.42f)
                                        drawCircle(Color(0xFF333333), radius = size.width * 0.28f)
                                    } else if (piece == GomokuGame.WHITE) {
                                        drawCircle(Color.White, radius = size.width * 0.42f)
                                        drawCircle(Color(0xFFCCCCCC), radius = size.width * 0.42f, style = Stroke(1.5.dp.toPx()))
                                    }

                                    // 高亮提示
                                    if (isHighlighted) {
                                        drawCircle(Color(0xFFFF4444), radius = size.width * 0.15f)
                                        drawCircle(Color.Transparent, radius = size.width * 0.45f,
                                            style = Stroke(2.dp.toPx()))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 求解结果列表（残局模式）
            if (solveResults.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("求解结果（点击高亮落子位置）：", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                solveResults.forEachIndexed { index, result ->
                    val marker = buildString {
                        if (result.isWinning) append("🏆胜")
                        if (result.isBlocking) append("🛡️挡")
                        if (!result.isWinning && !result.isBlocking) append("📍")
                    }
                    val coordLabel = "${('A' + result.x)}${result.y + 1}"
                    OutlinedButton(
                        onClick = { highlightedCell = result.x to result.y },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (highlightedCell == result.x to result.y)
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        else ButtonDefaults.outlinedButtonColors(),
                    ) {
                        Text(
                            "#${index + 1} $marker $coordLabel  |  攻:${result.attackScore} 防:${result.defenseScore}",
                            fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // 底部按钮
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mode == GomokuMode.PLAY) {
                    Button(onClick = { resetGame() }) { Text("重新开始") }
                } else {
                    Button(onClick = {
                        solveResults = emptyList()
                        highlightedCell = null
                    }) { Text("清除高亮") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { resetGame() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                    ) { Text("清空棋盘") }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                if (mode == GomokuMode.PLAY)
                    "你执黑先手，点击棋盘落子，AI 自动应手"
                else "残局模式：摆好棋子后选求解方 → 点「求解」\n也可拍照识别真实棋盘",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
