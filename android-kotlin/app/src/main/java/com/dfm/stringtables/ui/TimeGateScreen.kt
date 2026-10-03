package com.dfm.stringtables.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dfm.stringtables.data.TimeCheckResult
import com.dfm.stringtables.data.TimeCheckStatus
import com.dfm.stringtables.data.TimeGuard
import com.dfm.stringtables.ui.theme.Shape12
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class GatePhase { CHECKING, PASSED, BLOCKED }

/**
 * Alpha 定时过期版的启动闸门：
 * 先向公网 NTP 服务器校验网络时间，通过才显示主界面；否则停留在统一提示上，
 * 只允许「重新校验 / 退出程序」。运行期每 15 分钟再复核一次。
 */
@Composable
fun AlphaTimeGate(onExit: () -> Unit, content: @Composable () -> Unit) {
    var phase by remember { mutableStateOf(GatePhase.CHECKING) }
    var result by remember { mutableStateOf<TimeCheckResult?>(null) }
    var failures by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    suspend fun runCheck(startup: Boolean) {
        val r = TimeGuard.check()
        result = r
        if (r.isOk) {
            failures = 0
            phase = GatePhase.PASSED
            return
        }
        failures += 1
        // 启动时必须一次通过；运行期只有「连不上 NTP」允许连续失败两次才拦（容忍单次抖动），
        // 已过期 / 时钟被改立即拦截。
        val hardFailure = r.status != TimeCheckStatus.UNREACHABLE
        phase = if (startup || hardFailure || failures >= 2) GatePhase.BLOCKED else GatePhase.PASSED
    }

    LaunchedEffect(Unit) { runCheck(startup = true) }

    LaunchedEffect(phase) {
        if (phase != GatePhase.PASSED) return@LaunchedEffect
        while (true) {
            delay(15 * 60 * 1000L)
            runCheck(startup = false)
            if (phase == GatePhase.BLOCKED) break
        }
    }

    when (phase) {
        GatePhase.PASSED -> content()
        else -> TimeGateScreen(
            checking = phase == GatePhase.CHECKING,
            result = result,
            onRetry = { scope.launch { phase = GatePhase.CHECKING; runCheck(startup = true) } },
            onExit = onExit,
        )
    }
}

/** 校验中 / 校验未通过的全屏提示（配色与圆角沿用应用主题角色）。 */
@Composable
fun TimeGateScreen(
    checking: Boolean,
    result: TimeCheckResult?,
    onRetry: () -> Unit,
    onExit: () -> Unit,
) {
    val s = MaterialTheme.colorScheme
    Surface(modifier = Modifier.fillMaxSize(), color = s.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (checking) {
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(s.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        color = s.primary,
                        strokeWidth = 3.dp,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "正在校验版本有效期…",
                    style = MaterialTheme.typography.titleMedium,
                    color = s.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "通过 NTP 服务器获取网络时间（不信任本机时钟）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = s.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(s.errorContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    SymbolIcon(glyph = Sym.INFO, size = 46.dp, tint = s.onErrorContainer)
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "版本校验未通过",
                    style = MaterialTheme.typography.titleMedium,
                    color = s.onSurface,
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(Shape12)
                        .background(s.errorContainer)
                        .padding(16.dp),
                ) {
                    Text(
                        TimeGuard.EXPIRY_MESSAGE,
                        style = MaterialTheme.typography.bodyLarge,
                        color = s.onErrorContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!result?.detail.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        result.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = s.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onRetry, shape = Shape12) { Text("重新校验") }
                    Button(onClick = onExit, shape = Shape12) { Text("退出程序") }
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "三角洲行动 · StringTables",
                style = MaterialTheme.typography.bodySmall,
                color = s.onSurfaceVariant,
            )
        }
    }
}
