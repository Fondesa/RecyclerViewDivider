/*
 * Copyright (c) 2020 Giorgio Antonioli
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fondesa.recyclerviewdivider

import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.recyclerview.widget.RecyclerView

/**
 * Base implementation of [RecyclerView.ItemDecoration] which provides some utilities methods.
 * It also provides different implementations of [getItemOffsets] and [onDraw].
 *
 * @param asSpace true if the divider should behave as a space.
 */
public abstract class BaseDividerItemDecoration(
    @VisibleForTesting internal val asSpace: Boolean
) : RecyclerView.ItemDecoration() {
    private var attachStateListenerHolder: AttachStateListenerHolder? = null
    private var observerHolder: ObserverHolder? = null

    /**
     * Adds this decoration to the given [RecyclerView].
     * If this decoration was already added to the given [RecyclerView], it will be removed first.
     *
     * @param recyclerView the [RecyclerView] which will add this [RecyclerView.ItemDecoration].
     */
    public fun addTo(recyclerView: RecyclerView) {
        removeFrom(recyclerView)
        recyclerView.addItemDecoration(this)
    }

    /**
     * Removes this decoration from the given [RecyclerView].
     *
     * @param recyclerView the [RecyclerView] which will remove this [RecyclerView.ItemDecoration].
     */
    public fun removeFrom(recyclerView: RecyclerView) {
        recyclerView.removeItemDecoration(this)
    }

    /**
     * Variation of [RecyclerView.ItemDecoration.getItemOffsets] which validates the [RecyclerView] before being notified.
     *
     * @param layoutManager the [RecyclerView.LayoutManager] attached to the [RecyclerView].
     * @param outRect the [Rect] on which the offsets should be set.
     * @param itemView the [View] of the cell which needs to get its offsets.
     * @param itemCount the number of items in the adapter attached to the [RecyclerView].
     * @param itemIndex the index of the cell which needs to get its offsets.
     * @see [RecyclerView.ItemDecoration.getItemOffsets].
     */
    protected abstract fun getItemOffsets(
        layoutManager: RecyclerView.LayoutManager,
        outRect: Rect,
        itemView: View,
        itemCount: Int,
        itemIndex: Int
    )

    /**
     * Variation of [RecyclerView.ItemDecoration.onDraw] which validates the [RecyclerView] before being notified.
     * This method will be invoked only if the divider shouldn't behave as a space since, if it's a space, it shouldn't draw anything.
     *
     * @param canvas the [Canvas] on which the divider should be drawn.
     * @param recyclerView the [RecyclerView] which has added this decoration.
     * @param layoutManager the [RecyclerView.LayoutManager] attached to the [RecyclerView].
     * @param itemCount the number of items in the adapter attached to the [RecyclerView].
     * @see [RecyclerView.ItemDecoration.onDraw].
     */
    protected abstract fun onDraw(canvas: Canvas, recyclerView: RecyclerView, layoutManager: RecyclerView.LayoutManager, itemCount: Int)

    /**
     * Callback invoked when the the adapter's data changes.
     * Specifically, this callback is invoked every time a method of [RecyclerView.AdapterDataObserver] is invoked.
     */
    protected open fun onDataChanged() {
        // No-op.
    }

    final override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        parent.setupAttachStateListener()
        // Avoids to call super.getItemOffsets to avoid to pre-compute the layout position of the item.
        // To avoid to depend on the implementations of this class, set the offsets to zero by default.
        outRect.setEmpty()
        val adapter = parent.adapter ?: return
        adapter.setupDataObserver()
        val itemCount = state.itemCount
        if (itemCount == 0) return
        val layoutManager = parent.layoutManager ?: return
        val itemIndex = parent.getChildAdapterPositionOrNull(view) ?: return
        getItemOffsets(layoutManager = layoutManager, outRect = outRect, itemView = view, itemCount = itemCount, itemIndex = itemIndex)
    }

    final override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        super.onDraw(c, parent, state)
        parent.setupAttachStateListener()
        // The divider shouldn't be drawn if it's configured as a space.
        if (asSpace) return
        val adapter = parent.adapter ?: return
        adapter.setupDataObserver()
        val itemCount = state.itemCount
        if (itemCount == 0) return
        val layoutManager = parent.layoutManager ?: return
        onDraw(canvas = c, recyclerView = parent, layoutManager = layoutManager, itemCount = itemCount)
    }

    @Suppress("DEPRECATION")
    final override fun getItemOffsets(outRect: Rect, itemPosition: Int, parent: RecyclerView) {
        super.getItemOffsets(outRect, itemPosition, parent)
    }

    @Suppress("DEPRECATION")
    final override fun onDraw(c: Canvas, parent: RecyclerView) {
        super.onDraw(c, parent)
    }

    private fun RecyclerView.setupAttachStateListener() {
        // If the RecyclerView didn't change, we shouldn't add a new listener.
        if (this == attachStateListenerHolder?.recyclerView) return
        clearAttachStateListenerHolder()
        val listener = OnRecyclerViewDetachedFromWindow(::destroy)
        attachStateListenerHolder = AttachStateListenerHolder(this, listener)
        addOnAttachStateChangeListener(listener)
    }

    private fun RecyclerView.Adapter<*>.setupDataObserver() {
        // If the adapter didn't change, we shouldn't register a new observer.
        if (this == observerHolder?.adapter) return
        clearObserverHolder()
        val observer = DataObserver(::onDataChanged)
        observerHolder = ObserverHolder(this, observer)
        registerAdapterDataObserver(observer)
    }

    private fun clearAttachStateListenerHolder() {
        attachStateListenerHolder?.let { (recyclerView, listener) ->
            recyclerView.removeOnAttachStateChangeListener(listener)
        }
        attachStateListenerHolder = null
    }

    private fun clearObserverHolder() {
        observerHolder?.let { (adapter, observer) ->
            adapter.unregisterAdapterDataObserver(observer)
        }
        observerHolder = null
    }

    private fun destroy() {
        clearObserverHolder()
        clearAttachStateListenerHolder()
    }

    private data class AttachStateListenerHolder(
        val recyclerView: RecyclerView,
        val listener: View.OnAttachStateChangeListener
    )

    private data class ObserverHolder(
        val adapter: RecyclerView.Adapter<*>,
        val observer: RecyclerView.AdapterDataObserver
    )

    private class DataObserver(private val onDataChanged: () -> Unit) : RecyclerView.AdapterDataObserver() {
        override fun onChanged() = onDataChanged()
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) = onDataChanged()
        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) = onDataChanged()
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = onDataChanged()
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = onDataChanged()
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) = onDataChanged()
    }

    private class OnRecyclerViewDetachedFromWindow(private val onDetach: () -> Unit) : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) = onDetach()
    }
}
