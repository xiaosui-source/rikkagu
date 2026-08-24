/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/xiaosui-source/rikkagu)，原作者 xiaosui-source
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.game

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor

/**
 * 五子棋游戏引擎 — 支持对战、残局求解、拍照识别.
 *
 * 棋盘 15x15；EMPTY=0, BLACK=1, WHITE=2。
 */
object GomokuGame {
    const val BOARD_SIZE = 15
    const val EMPTY = 0
    const val BLACK = 1
    const val WHITE = 2

    // 评分常量
    private const val SCORE_FIVE = 10000000
    private const val SCORE_LIVE_FOUR = 100000
    private const val SCORE_RUSH_FOUR = 50000
    private const val SCORE_LIVE_THREE = 10000
    private const val SCORE_SLEEP_THREE = 2000
    private const val SCORE_LIVE_TWO = 1000
    private const val SCORE_SLEEP_TWO = 200

    private val DIRS = arrayOf(
        intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(1, -1),
        intArrayOf(0, -1), intArrayOf(-1, 0), intArrayOf(-1, -1), intArrayOf(-1, 1),
    )

    fun inBoard(x: Int, y: Int): Boolean = x in 0 until BOARD_SIZE && y in 0 until BOARD_SIZE

    fun isBoardFull(board: Array<IntArray>): Boolean {
        for (row in board) for (c in row) if (c == EMPTY) return false
        return true
    }

    fun checkWin(board: Array<IntArray>, player: Int, x: Int, y: Int): Boolean {
        for (d in 0..3) {
            val dx = DIRS[d][0]
            val dy = DIRS[d][1]
            var count = 1
            var nx = x + dx; var ny = y + dy
            while (inBoard(nx, ny) && board[nx][ny] == player) { count++; nx += dx; ny += dy }
            nx = x - dx; ny = y - dy
            while (inBoard(nx, ny) && board[nx][ny] == player) { count++; nx -= dx; ny -= dy }
            if (count >= 5) return true
        }
        return false
    }

    fun isWinningMove(board: Array<IntArray>, player: Int, x: Int, y: Int): Boolean =
        checkWin(board, player, x, y)

    /** 检查任意玩家是否已获胜（用于残局验证） */
    fun checkAnyWin(board: Array<IntArray>): Int {
        for (x in 0 until BOARD_SIZE) {
            for (y in 0 until BOARD_SIZE) {
                if (board[x][y] != EMPTY && checkWin(board, board[x][y], x, y))
                    return board[x][y]
            }
        }
        return 0
    }

    /** AI 最佳落子 */
    fun getBestMove(board: Array<IntArray>, player: Int): Pair<Int, Int>? {
        val candidates = generateCandidates(board)
        if (candidates.isEmpty()) return null

        var best = candidates.first()
        var bestScore = Int.MIN_VALUE
        val opponent = if (player == BLACK) WHITE else BLACK

        for ((x, y) in candidates) {
            board[x][y] = player
            val myScore = evaluatePoint(board, player, x, y)
            if (checkWin(board, player, x, y)) {
                board[x][y] = EMPTY
                return x to y
            }
            val oppScore = evaluatePoint(board, opponent, x, y)
            board[x][y] = EMPTY
            val score = myScore + oppScore / 2
            if (score > bestScore) {
                bestScore = score
                best = x to y
            }
        }
        return best
    }

    /**
     * 残局求解：给定棋盘和当前轮到的一方，返回最佳落子 + 若干候选.
     * 返回 [MoveResult] 列表，按评分降序。
     */
    fun solveEndgame(board: Array<IntArray>, player: Int, topN: Int = 5): List<MoveResult> {
        val candidates = generateCandidates(board)
        if (candidates.isEmpty()) return emptyList()

        val results = mutableListOf<MoveResult>()
        val opponent = if (player == BLACK) WHITE else BLACK

        for ((x, y) in candidates) {
            board[x][y] = player
            val myScore = evaluatePoint(board, player, x, y)
            val isWinning = checkWin(board, player, x, y)
            board[x][y] = EMPTY

            // 也评估防守价值
            board[x][y] = opponent
            val oppThreat = evaluatePoint(board, opponent, x, y)
            val oppWinning = checkWin(board, opponent, x, y)
            board[x][y] = EMPTY

            val totalScore = myScore * 2 + oppThreat
            results.add(
                MoveResult(
                    x = x, y = y,
                    score = totalScore,
                    isWinning = isWinning,
                    isBlocking = oppWinning,
                    attackScore = myScore,
                    defenseScore = oppThreat,
                )
            )
        }
        return results.sortedByDescending { it.score }.take(topN)
    }

    /**
     * 拍照识别棋盘：从 Bitmap 中检测棋盘网格和棋子.
     *
     * 算法：将图片等分为 15x15 网格，在每个交叉点采样像素，
     * 根据亮度判断是黑子/白子/空位。
     */
    fun recognizeBoard(bitmap: Bitmap): BoardRecognitionResult {
        val scaled = Bitmap.createScaledBitmap(bitmap, 450, 450, true)
        val board = Array(BOARD_SIZE) { IntArray(BOARD_SIZE) { EMPTY } }
        val cellSize = 450f / (BOARD_SIZE + 1)
        val confidenceScores = mutableListOf<Float>()

        for (x in 0 until BOARD_SIZE) {
            for (y in 0 until BOARD_SIZE) {
                val px = ((x + 1) * cellSize).toInt().coerceIn(0, 449)
                val py = ((y + 1) * cellSize).toInt().coerceIn(0, 449)

                // 采样 3x3 区域取平均值
                var rSum = 0; var gSum = 0; var bSum = 0; var samples = 0
                for (dx in -2..2) {
                    for (dy in -2..2) {
                        val sx = (px + dx).coerceIn(0, 449)
                        val sy = (py + dy).coerceIn(0, 449)
                        val pixel = scaled.getPixel(sx, sy)
                        rSum += AndroidColor.red(pixel)
                        gSum += AndroidColor.green(pixel)
                        bSum += AndroidColor.blue(pixel)
                        samples++
                    }
                }
                val avgR = rSum / samples
                val avgG = gSum / samples
                val avgB = bSum / samples
                val brightness = (avgR + avgG + avgB) / 3f

                // 判断：暗色→黑子，亮色→白子（需足够偏离背景）
                if (brightness < 80) {
                    board[x][y] = BLACK
                    confidenceScores.add((80 - brightness) / 80f)
                } else if (brightness > 200) {
                    board[x][y] = WHITE
                    confidenceScores.add((brightness - 200) / 55f)
                } else {
                    board[x][y] = EMPTY
                    confidenceScores.add(1f - kotlin.math.abs(brightness - 140) / 140f)
                }
            }
        }

        val avgConfidence = if (confidenceScores.isNotEmpty()) confidenceScores.average().toFloat() else 0f
        return BoardRecognitionResult(board, avgConfidence)
    }

    private fun generateCandidates(board: Array<IntArray>): List<Pair<Int, Int>> {
        val set = LinkedHashSet<Pair<Int, Int>>()
        for (x in 0 until BOARD_SIZE) {
            for (y in 0 until BOARD_SIZE) {
                if (board[x][y] != EMPTY) {
                    for (d in DIRS) {
                        val nx = x + d[0]; val ny = y + d[1]
                        if (inBoard(nx, ny) && board[nx][ny] == EMPTY) set.add(nx to ny)
                    }
                }
            }
        }
        if (set.isEmpty()) set.add(BOARD_SIZE / 2 to BOARD_SIZE / 2)
        return set.toList()
    }

    private fun evaluatePoint(board: Array<IntArray>, player: Int, x: Int, y: Int): Int {
        var score = 0
        for (d in 0..3) score += evaluateDirection(board, player, x, y, d)
        return score
    }

    private fun evaluateDirection(board: Array<IntArray>, player: Int, x: Int, y: Int, d: Int): Int {
        val dx = DIRS[d][0]; val dy = DIRS[d][1]
        var count = 1; var openEnds = 0
        var nx = x + dx; var ny = y + dy
        while (inBoard(nx, ny) && board[nx][ny] == player) { count++; nx += dx; ny += dy }
        if (inBoard(nx, ny) && board[nx][ny] == EMPTY) openEnds++
        nx = x - dx; ny = y - dy
        while (inBoard(nx, ny) && board[nx][ny] == player) { count++; nx -= dx; ny -= dy }
        if (inBoard(nx, ny) && board[nx][ny] == EMPTY) openEnds++
        return when {
            count >= 5 -> SCORE_FIVE
            count == 4 -> if (openEnds == 2) SCORE_LIVE_FOUR else SCORE_RUSH_FOUR
            count == 3 -> if (openEnds == 2) SCORE_LIVE_THREE else SCORE_SLEEP_THREE
            count == 2 -> if (openEnds == 2) SCORE_LIVE_TWO else SCORE_SLEEP_TWO
            else -> 0
        }
    }
}

/** 落子结果 */
data class MoveResult(
    val x: Int, val y: Int,
    val score: Int,
    val isWinning: Boolean,
    val isBlocking: Boolean,
    val attackScore: Int,
    val defenseScore: Int,
)

/** 拍照识别结果 */
data class BoardRecognitionResult(
    val board: Array<IntArray>,
    val confidence: Float, // 0-1
)
