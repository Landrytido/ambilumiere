package com.gathin.lights.tablet;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * Children in equal cells, cols to a row. With rowHeight 0 the cells share the height they are given (the cards of the
 * home screen), never taller than maxAspect times their width, the block centred; with a rowHeight each row has that height
 * and the grid is as tall as its rows.
 */
final class FlowGrid extends ViewGroup {
    private final int cols;
    private final int gap;
    private final int rowHeight;
    private final float maxAspect;
    private int cell, cellH, top;

    FlowGrid(Context c, int cols, int gapDp, int rowHeightDp, float maxAspect) {
        super(c);
        this.cols = Math.max(1, cols);
        this.gap = Draw.dp(gapDp);
        this.rowHeight = Draw.dp(rowHeightDp);
        this.maxAspect = maxAspect;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int n = getChildCount();
        int rows = Math.max(1, (n + cols - 1) / cols);
        int w = MeasureSpec.getSize(widthSpec);
        cell = (w - gap * (cols - 1)) / cols;
        int total;
        if (rowHeight > 0) {
            cellH = rowHeight;
            total = rows * cellH + gap * (rows - 1);
            top = 0;
        } else {
            int h = MeasureSpec.getSize(heightSpec);
            cellH = Math.min((h - gap * (rows - 1)) / rows, Math.round(cell * maxAspect));
            int block = rows * cellH + gap * (rows - 1);
            top = Math.max(0, (h - block) / 2);
            total = h;
        }
        for (int i = 0; i < n; i++) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cell, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(cellH, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(w, total);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            int x = (i % cols) * (cell + gap), y = top + (i / cols) * (cellH + gap);
            v.layout(x, y, x + cell, y + cellH);
        }
    }
}
