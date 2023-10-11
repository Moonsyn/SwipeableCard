package com.liam.swipecard

import android.os.Bundle
import android.view.VelocityTracker
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.liam.swipecard.ui.theme.SwipeCardTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SwipeCardTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SwipeableCards()
                }
            }
        }
    }
}

// 원본 코드 참고
// https://www.jetpackcompose.app/snippets/SwipeableCards
// 이것저것 만져보면서 고친거라 저도 100% 코드를 이해한 건 아니라 주석이 많지 않습니다...
@Preview
@Composable
fun SwipeableCards() {
    var colors by remember {
        mutableStateOf(
            listOf(
                Color(0xff90caf9),
                Color(0xfffafafa),
                Color(0xffef9a9a),
                Color(0xfffff59d),
            ).reversed()
        )
    }
    Box(
        Modifier
            .background(Color.Black)
            .padding(vertical = 32.dp)
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        colors.forEachIndexed { idx, color ->
            key(color) {
                SwipeableCard(order = idx,
                    totalCount = colors.size,
                    backgroundColor = color,
                    onMoveToRemove = {
                        // 카드 하나 지우면 list에서 해당 카드(color) 제거
                        // 카드가 1개만 남으면 그대로.
                        if (colors.size > 1) colors = (colors - color)
                    })
            }
        }
    }
}

@Composable
fun SwipeableCard(
    order: Int,
    totalCount: Int,
    backgroundColor: Color = Color.White,
    onMoveToRemove: () -> Unit
) {
    val animatedScale by animateFloatAsState(
        targetValue = 1f - (totalCount - order) * 0.05f, label = "",
    )
    val animatedXOffset by animateDpAsState(
        targetValue = ((totalCount - order) * 12).dp, label = "",
    )
    Box(
        modifier = Modifier
            .offset { IntOffset(x = animatedXOffset.roundToPx(), y = 0) }
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .swipeToRemove { onMoveToRemove() }
    ) {
        SampleCard(backgroundColor = backgroundColor)
    }
}

@Composable
fun SampleCard(backgroundColor: Color = Color.White) {
    Card(
        modifier = Modifier
            .width(320.dp)
            .height(360.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(vertical = 24.dp, horizontal = 32.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(0.5f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .pillShape()
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Box(
                        Modifier
                            .height(12.dp)
                            .fillMaxWidth()
                            .pillShape()
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .height(12.dp)
                            .fillMaxWidth(0.6f)
                            .pillShape()
                    )
                }
            }
        }
    }
}

fun Modifier.pillShape() = this.then(
    background(Color.Black.copy(0.3f), CircleShape)
)

fun Modifier.swipeToRemove(
    onMoveToRemove: () -> Unit
): Modifier = composed {
    val offsetX = remember { Animatable(0f) }
    var leftSide by remember { mutableStateOf(true) }
    var clearedHurdle by remember { mutableStateOf(false) }
    pointerInput(Unit) {
        val decay = splineBasedDecay<Float>(this)
        coroutineScope {
            while (true) {
                offsetX.stop()
                val velocityTracker = VelocityTracker.obtain()
                awaitPointerEventScope {
                    horizontalDrag(awaitFirstDown().id) { change ->
                        val horizontalDragOffset = offsetX.value + change.positionChange().x
                        val horizontalPosition = change.previousPosition.x
                        leftSide = horizontalPosition <= size.width / 2
                        launch {
                            offsetX.snapTo(horizontalDragOffset)
                        }
                        if (change.positionChange() != Offset.Zero) change.consume()
                    }
                }
                val velocity = velocityTracker.xVelocity
                velocityTracker.recycle()
                val targetOffsetX = decay.calculateTargetValue(offsetX.value, velocity)
                if (targetOffsetX.absoluteValue <= size.width) {
                    // Not enough velocity; Reset.
                    launch { offsetX.animateTo(targetValue = 0f, initialVelocity = velocity) }
                } else {
                    // Enough velocity to remove the card
                    val duration = 600
                    val maxDistanceToFling = (size.width * 2.0).toFloat()
                    val easeInOutEasing = CubicBezierEasing(0.42f, 0.0f, 0.58f, 1.0f)
                    val distanceToFling = min(
                        targetOffsetX.absoluteValue + size.height, maxDistanceToFling
                    )
                    val animationJobs = listOf(
                        launch {
                            offsetX.animateTo(targetValue = 0f,
                                initialVelocity = velocity,
                                animationSpec = keyframes {
                                    durationMillis = duration
                                    -distanceToFling at (duration / 2) with easeInOutEasing
                                    40f at duration - 70
                                }
                            ) {
                                if (value <= -size.width * 2 && !clearedHurdle) {
                                    onMoveToRemove()
                                    clearedHurdle = true
                                }
                            }
                        }
                    )
                    animationJobs.joinAll()
                    clearedHurdle = false
                }
            }
        }
    }
        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
        .graphicsLayer {
            transformOrigin = TransformOrigin.Center
        }
}