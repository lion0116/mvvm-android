package com.mylive.live.widget

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.database.DataSetObservable
import android.database.DataSetObserver
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import kotlin.math.abs

/**
 * Created by Developer Zailong Shi on 2020/5/9.
 */
class SpinGallery @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var paint: Paint? = null

    init {
        isChildrenDrawingOrderEnabled = true
        if (isInEditMode) {
            paint = Paint().apply { isAntiAlias = true }
            setWillNotDraw(false)
        }
    }

    private var viewHolders: Array<ViewHolder>? = null
    private var adapter: Adapter<*>? = null
    private var animator: Animator? = null
    private var direction: Direction = Direction.AUTO

    /**
     * 圆的半径（子视图分布在该圆上），范围值大于等于0，默认值是0
     */
    var radius: Int = 0
        set(value) {
            field = if (value < 0) 0 else value
            updateLayout()
        }

    /**
     * 景深，范围值0到1，默认值0.5
     */
    var depth: Float = 0.5f
        set(value) {
            field = when {
                value < 0f -> 0f
                value > 1f -> 1f
                else -> value
            }
            updateLayout()
        }

    /**
     * 切换动画执行时长
     */
    var animatorDuration: Long = 500

    /**
     * 切换过程回调，提供用户定制切换运动轨迹等
     */
    var onLayoutUpdateCallback: ((holder: ViewHolder, radius: Int, depth: Float, angle: Int) -> Unit)? = null

    private var currentPosition: Int = -1
        set(value) {
            adapter {
                val itemCount = getItemCount()
                field = when {
                    value < 0 -> 0
                    value >= itemCount -> itemCount - 1
                    else -> value
                }
                createViewHolders(this@SpinGallery)
                createAnimatorIfNeeded(field, itemCount)
                bindViewHolders(this@SpinGallery, field, itemCount)
            }
        }

    private fun createAnimatorIfNeeded(currentPosition: Int, itemCount: Int) = when {
        itemCount <= 1 -> null
        else -> viewHolders {
            last { it.location == Location.FRONT }.apply {
                if (position != -1 && position != currentPosition) {
                    when (this@SpinGallery.direction) {
                        //视图切换方向是自动时，计算出向前和向后的间隔，哪个方向更近选哪个
                        Direction.AUTO -> {
                            if (position < currentPosition) {
                                if (currentPosition - position
                                        <= position + (itemCount - currentPosition)) {
                                    Direction.FORWARD
                                } else {
                                    Direction.BACKWARD
                                }
                            } else {
                                if (position - currentPosition
                                        <= currentPosition + (itemCount - position)) {
                                    Direction.BACKWARD
                                } else {
                                    Direction.FORWARD
                                }
                            }
                        }
                        else -> this@SpinGallery.direction
                    }.apply direction@{
                        forEach {
                            when (Direction.BACKWARD) {
                                this@direction -> location--
                                else -> location++
                            }
                        }.also {
                            sortWith(Comparator { o1, o2 ->
                                when {
                                    //切换到上一个视图的情况，左、右视图的绘制顺序相反
                                    o1.location.isLeftOrRight && o2.location.isLeftOrRight
                                            && this@direction == Direction.BACKWARD -> {
                                        o2.location - o1.location
                                    }
                                    else -> o1.location - o2.location
                                }
                            }).also {
                                postInvalidate()
                            }
                        }.also {
                            animator?.end()
                            animator = ValueAnimator.ofInt(
                                    //向前切换视图，即视图向左滚动，从90度到0；否则从-90度到0
                                    if (this@direction == Direction.FORWARD) 90 else -90, 0
                            ).apply {
                                addUpdateListener {
                                    val angle = it.animatedValue
                                    updateLayout(angle as Int)
                                }
                                duration = animatorDuration
                                start()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (animator == null || !animator!!.isRunning) {
            updateLayout()
        }
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if (isInEditMode && 0 == childCount) {
            canvas {
                drawCircle(
                        .9f * -.75f * measuredHeight + 1f * measuredWidth / 2,
                        1f * measuredHeight / 2,
                        .75f * measuredHeight / 2,
                        paint { color = 0xff666666.toInt() }!!
                )
                drawCircle(
                        .9f * .75f * measuredHeight + 1f * measuredWidth / 2,
                        1f * measuredHeight / 2,
                        .75f * measuredHeight / 2,
                        paint { color = 0xff666666.toInt() }!!
                )
                drawCircle(
                        1f * measuredWidth / 2,
                        1f * measuredHeight / 2,
                        1f * measuredHeight / 2,
                        paint { color = 0xff999999.toInt() }!!
                )
            }
        }
    }

    override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int =
            viewHolders?.let { indexOfChild(it[drawingPosition].itemView) } ?: drawingPosition

    private fun updateLayout(angle: Int = 0) = viewHolders?.forEach {
        val depth = depth
        val ratio = 1f * angle / 90
        val halfOfDepth = (1f - depth) / 2
        val radius: Int = when (radius) {
            0 -> .9f * itemView.measuredWidth * (depth + halfOfDepth)
            else -> 1f * radius
        }.toInt()
        when (location) {
            Location.BEHIND -> itemView {
                translationX = -1 * ratio * radius
                scaleX = depth + halfOfDepth * abs(ratio)
                scaleY = scaleX
                alpha = 1f//abs(ratio)
            }
            Location.LEFT -> itemView {
                translationX = -radius * (1f - abs(ratio))
                scaleX = when {
                    ratio > 0 -> 1 - halfOfDepth * (1f - ratio)
                    else -> depth + (halfOfDepth * (1f + ratio))
                }
                scaleY = scaleX
                alpha = if (adapter!!.getItemCount() <= 1) 0f else 1f
            }
            Location.RIGHT -> itemView {
                translationX = radius * (1f - abs(ratio))
                scaleX = when {
                    ratio > 0 -> depth + (halfOfDepth * (1f - ratio))
                    else -> 1f - halfOfDepth * (1f + ratio)
                }
                scaleY = scaleX
                alpha = if (adapter!!.getItemCount() <= 1) 0f else 1f
            }
            Location.FRONT -> itemView {
                translationX = radius * ratio
                scaleX = 1f - halfOfDepth * abs(ratio)
                scaleY = scaleX
                alpha = 1f
            }
        }
        onLayoutUpdateCallback?.invoke(this, radius, depth, angle)
    }

    fun getAdapter() = adapter

    fun <T : ViewHolder> setAdapter(adapter: Adapter<T>) {
        if (this.adapter != adapter) {
            this.adapter?.unregisterAll()
            this.adapter = adapter//先赋值再通知更新
            this.adapter {
                registerObserver(object : DataSetObserver() {
                    override fun onInvalidated() {
                        viewHolders = null
                        onChanged()
                    }

                    override fun onChanged() {
                        viewHolders?.forEach { position = -1 }
                        currentPosition = 0
                    }
                })
                notifyInvalidated()
            }
        }
    }

    /**
     *  切换到上一个视图
     */
    fun backward() = adapter?.run {
        val itemCount = getItemCount()
        if (itemCount > 1) {
            var prev = currentPosition - 1
            if (prev < 0) {
                prev = itemCount - 1
            }
            direction = Direction.BACKWARD
            currentPosition = prev
        }
    }

    /**
     * 切换到下一个视图
     */
    fun forward() = adapter?.run {
        val itemCount = getItemCount()
        if (itemCount > 1) {
            var next = currentPosition + 1
            if (next >= itemCount) {
                next = 0
            }
            direction = Direction.FORWARD
            currentPosition = next
        }
    }

    /**
     * 切换到指定子视图
     */
    fun setCurrentItem(position: Int) {
        direction = Direction.AUTO
        currentPosition = position
    }

    override fun generateDefaultLayoutParams() = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
    )

    class LayoutParams : FrameLayout.LayoutParams {
        constructor(c: Context, attrs: AttributeSet?) : super(c, attrs)
        constructor(width: Int, height: Int, gravity: Int) : super(width, height, gravity)
        constructor(width: Int, height: Int) : super(width, height)

        @RequiresApi(Build.VERSION_CODES.KITKAT)
        constructor(source: FrameLayout.LayoutParams) : super(source)
        constructor(source: ViewGroup.LayoutParams) : super(source)
        constructor(source: MarginLayoutParams) : super(source)
    }

    abstract class Adapter<T : ViewHolder> : DataSetObservable() {
        internal fun createViewHolders(parent: SpinGallery) = parent {
            viewHolders ?: synchronized(this) {
                viewHolders ?: Array<ViewHolder>(4) {
                    onCreateViewHolder(this).apply {
                        when (it) {
                            0 -> location = Location.BEHIND
                            1 -> location = Location.RIGHT
                            2 -> location = Location.LEFT
                            3 -> location = Location.FRONT
                        }
                    }
                }.apply {
                    removeAllViews()
                    forEach {
                        makeLayoutParams(parent, this).apply {
                            if (this.parent == null) {
                                addView(this)
                            } else if (this.parent != parent) {
                                (this.parent as ViewGroup).removeView(this)
                                addView(this)
                            }
                        }
                    }
                }.also {
                    viewHolders = it
                }
            }
        }

        internal fun bindViewHolders(parent: SpinGallery, currentPosition: Int, itemCount: Int) =
                parent.viewHolders!!.forEach {
                    when (location) {
                        Location.FRONT -> {
                            if (currentPosition != position) {
                                bindViewHolder(this, currentPosition)
                            }
                        }
                        Location.LEFT -> {
                            if (itemCount <= 1) {
                                return@forEach
                            }
                            var leftPosition = currentPosition - 1
                            if (leftPosition < 0) {
                                leftPosition = itemCount - 1
                            }
                            if (leftPosition != position) {
                                bindViewHolder(this, leftPosition)
                            }
                        }
                        Location.RIGHT -> {
                            if (itemCount <= 1) {
                                return@forEach
                            }
                            var rightPosition = currentPosition + 1
                            if (rightPosition >= itemCount) {
                                rightPosition = 0
                            }
                            if (rightPosition != position) {
                                bindViewHolder(this, rightPosition)
                            }
                        }
                        Location.BEHIND -> {
                            position = -1
                        }
                    }
                }

        private fun bindViewHolder(viewHolder: ViewHolder, position: Int) =
                @Suppress("UNCHECKED_CAST")
                onBindViewHolder(viewHolder as T, position).also {
                    viewHolder.position = position
                }

        private fun makeLayoutParams(parent: SpinGallery, viewHolder: ViewHolder) =
                viewHolder.itemView.apply {
                    layoutParams = when (layoutParams) {
                        is FrameLayout.LayoutParams ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                LayoutParams(layoutParams as FrameLayout.LayoutParams)
                            } else {
                                LayoutParams(layoutParams as MarginLayoutParams)
                            }
                        is MarginLayoutParams -> LayoutParams(layoutParams as MarginLayoutParams)
                        is ViewGroup.LayoutParams -> LayoutParams(layoutParams)
                        else -> parent.generateDefaultLayoutParams()
                    }.apply {
                        gravity = Gravity.CENTER
                    }
                }

        abstract fun onCreateViewHolder(parent: ViewGroup): T
        abstract fun onBindViewHolder(holder: T, position: Int)
        abstract fun getItemCount(): Int
    }

    open class ViewHolder @JvmOverloads constructor(
            val itemView: View, var position: Int = -1, var location: Location = Location.FRONT
    )

    //子视图方位：前、后、左、右
    enum class Location(private val value: Int) {
        FRONT(3), BEHIND(0), LEFT(2), RIGHT(1);

        val isLeftOrRight: Boolean get() = this == LEFT || this == RIGHT
        operator fun minus(other: Location) = value - other.value

        operator fun inc() = when (this) {
            LEFT -> BEHIND
            BEHIND -> RIGHT
            RIGHT -> FRONT
            FRONT -> LEFT
        }

        operator fun dec() = when (this) {
            LEFT -> FRONT
            FRONT -> RIGHT
            RIGHT -> BEHIND
            BEHIND -> LEFT
        }
    }

    //视图切换方向：向后、自动、向前
    enum class Direction { BACKWARD, AUTO, FORWARD }
}

inline operator fun <T> T?.invoke(block: T.() -> Unit) = this?.apply(block)
inline fun <T> Array<out T>.forEach(action: T.() -> Unit) = forEach { item -> action(item) }