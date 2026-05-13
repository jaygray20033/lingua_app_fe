package com.lingua.app.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/**
 * U10 FIX: một LinearLayout đơn giản hỗ trợ wrap các child sang dòng mới
 * khi không đủ chỗ ngang — giống FlexboxLayout cơ bản nhưng không cần
 * thêm dependency. Dùng cho sentence-order tokens, có thể nhiều và dài.
 */
public class WrapLayout extends LinearLayout {
    public WrapLayout(Context c) { super(c); setOrientation(HORIZONTAL); }
    public WrapLayout(Context c, AttributeSet a) { super(c, a); setOrientation(HORIZONTAL); }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();
        int paddingTop = getPaddingTop();
        int paddingBottom = getPaddingBottom();

        int availableWidth = widthSize - paddingLeft - paddingRight;

        int childCount = getChildCount();
        int rowWidth = 0;
        int rowHeight = 0;
        int totalHeight = 0;
        int maxRowWidth = 0;

        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;

            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            int cw = child.getMeasuredWidth();
            int ch = child.getMeasuredHeight();
            int ml = 0, mr = 0, mt = 0, mb = 0;
            ViewGroup.LayoutParams lp = child.getLayoutParams();
            if (lp instanceof MarginLayoutParams) {
                ml = ((MarginLayoutParams) lp).leftMargin;
                mr = ((MarginLayoutParams) lp).rightMargin;
                mt = ((MarginLayoutParams) lp).topMargin;
                mb = ((MarginLayoutParams) lp).bottomMargin;
            }

            int totalChildW = cw + ml + mr;
            if (rowWidth + totalChildW > availableWidth && rowWidth > 0) {
                totalHeight += rowHeight;
                maxRowWidth = Math.max(maxRowWidth, rowWidth);
                rowWidth = 0;
                rowHeight = 0;
            }
            rowWidth += totalChildW;
            rowHeight = Math.max(rowHeight, ch + mt + mb);
        }
        totalHeight += rowHeight;
        maxRowWidth = Math.max(maxRowWidth, rowWidth);

        int resolvedW = resolveSize(maxRowWidth + paddingLeft + paddingRight, widthMeasureSpec);
        int resolvedH = resolveSize(totalHeight + paddingTop + paddingBottom, heightMeasureSpec);
        setMeasuredDimension(resolvedW, resolvedH);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        int paddingRight = getPaddingRight();
        int availableWidth = r - l - paddingLeft - paddingRight;

        int curX = paddingLeft;
        int curY = paddingTop;
        int rowHeight = 0;

        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int cw = child.getMeasuredWidth();
            int ch = child.getMeasuredHeight();
            int ml = 0, mr = 0, mt = 0, mb = 0;
            ViewGroup.LayoutParams lp = child.getLayoutParams();
            if (lp instanceof MarginLayoutParams) {
                ml = ((MarginLayoutParams) lp).leftMargin;
                mr = ((MarginLayoutParams) lp).rightMargin;
                mt = ((MarginLayoutParams) lp).topMargin;
                mb = ((MarginLayoutParams) lp).bottomMargin;
            }
            int totalChildW = cw + ml + mr;
            if (curX + totalChildW > paddingLeft + availableWidth && curX > paddingLeft) {
                curX = paddingLeft;
                curY += rowHeight;
                rowHeight = 0;
            }
            int childLeft = curX + ml;
            int childTop = curY + mt;
            child.layout(childLeft, childTop, childLeft + cw, childTop + ch);
            curX += totalChildW;
            rowHeight = Math.max(rowHeight, ch + mt + mb);
        }
    }
}
