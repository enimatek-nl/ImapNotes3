/*
 * Copyright (C) 2026 - ImapNotes3 contributors
 *
 * This file is part of ImapNotes3.
 *
 * ImapNotes3 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.niendo.ImapNotes3.Miscs;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ListAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A list whose rows of type {@link SwipeRevealRow} can be swiped sideways to reveal their action
 * buttons, like the note lists of the iOS Notes app.
 * <p>
 * The gesture is recognized here and not inside the row on purpose: a row only receives move events
 * when one of its children is clickable, otherwise the list itself handles the touch stream for
 * scrolling. Recognizing the drag in the list keeps vertical scrolling, swipe-to-refresh, tapping a
 * row and dragging a row working independently of each other.
 */
public class SwipeRevealListView extends ListView {
    private static final String TAG = "IN_SwipeRevealListView";

    // No gesture in progress.
    private static final int STATE_REST = 0;
    // Finger is down, but it is not clear yet whether the user wants to swipe or to scroll.
    private static final int STATE_PENDING = 1;
    // A row is being dragged, the list must neither scroll nor click.
    private static final int STATE_SWIPING = 2;
    // The list scrolls, the rows are left alone.
    private static final int STATE_SCROLLING = 3;

    private final int touchSlop;
    private int state = STATE_REST;
    private float downX;
    private float downY;
    private float lastX;
    @Nullable
    private SwipeRevealRow swipeRow;

    public SwipeRevealListView(@NonNull Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public SwipeRevealListView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public SwipeRevealListView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull MotionEvent ev) {
        // The DOWN always arrives here, also when a button of an already revealed row is the one that
        // receives the touch, so a revealed row can be dragged closed again.
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startTouch(ev);
                break;
            case MotionEvent.ACTION_MOVE:
                if (state == STATE_PENDING && isHorizontalDrag(ev)) {
                    beginSwipe(ev);
                }
                break;
            default:
                break;
        }
        return state == STATE_SWIPING || super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (state == STATE_REST) {
                    startTouch(ev);
                }
                return super.onTouchEvent(ev);
            case MotionEvent.ACTION_MOVE:
                if (state == STATE_REST) {
                    // A DOWN that was not seen here at all: treat the current position as the start
                    // of the gesture.
                    startTouch(ev);
                }
                if (state == STATE_PENDING) {
                    if (isHorizontalDrag(ev)) {
                        beginSwipe(ev);
                    } else if (isVerticalDrag(ev)) {
                        state = STATE_SCROLLING;
                    }
                }
                if (state == STATE_SWIPING) {
                    dragRow(ev);
                    return true;
                }
                return super.onTouchEvent(ev);
            case MotionEvent.ACTION_UP:
                if (state == STATE_SWIPING) {
                    finishSwipe();
                    return true;
                }
                if (isTap(ev) && SwipeRevealRow.concealRevealedRow()) {
                    // While a row is open, a tap only closes it; it must not open the item.
                    Log.d(TAG, "onTouchEvent: the tap closed the revealed row");
                    passCancelToSuper(ev);
                    state = STATE_REST;
                    return true;
                }
                if (isTap(ev)) {
                    // AbsListView turns this touch into the item click itself; the call here only
                    // serves the accessibility services that act on performClick().
                    performClick();
                }
                state = STATE_REST;
                return super.onTouchEvent(ev);
            case MotionEvent.ACTION_CANCEL:
                if (state == STATE_SWIPING) {
                    finishSwipe();
                    return true;
                }
                state = STATE_REST;
                return super.onTouchEvent(ev);
            default:
                return super.onTouchEvent(ev);
        }
    }

    /**
     * A touch gesture is turned into an item click by {@link ListView} itself, so {@code
     * performClick()} is the entry point for the other ways of activating a row - an accessibility
     * service or an external keyboard.  Without this they would do nothing, because a row is not
     * clickable and its action buttons stay hidden until the row is swiped open.
     */
    @Override
    public boolean performClick() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.isAccessibilityFocused()) {
                int position = getPositionForView(child);
                ListAdapter adapter = getAdapter();
                if (position != INVALID_POSITION && adapter != null) {
                    Log.v(TAG, "performClick: activating the row at position " + position);
                    return performItemClick(child, position, adapter.getItemId(position));
                }
            }
        }
        return super.performClick();
    }

    private void startTouch(@NonNull MotionEvent ev) {
        state = STATE_PENDING;
        downX = ev.getX();
        downY = ev.getY();
        swipeRow = null;
    }

    /**
     * Takes the gesture away from the list and starts moving the row under the finger.
     */
    private void beginSwipe(@NonNull MotionEvent ev) {
        swipeRow = findRowAt(downX, downY);
        if (swipeRow == null) {
            Log.v(TAG, "beginSwipe: no swipe row under the touch");
            state = STATE_SCROLLING;
            return;
        }
        state = STATE_SWIPING;
        swipeRow.dragContentBy(ev.getX() - downX);
        lastX = ev.getX();
        // Let the list forget about the press, so the swipe neither scrolls nor clicks the row.
        passCancelToSuper(ev);
    }

    private void dragRow(@NonNull MotionEvent ev) {
        if (swipeRow != null) {
            swipeRow.dragContentBy(ev.getX() - lastX);
        }
        lastX = ev.getX();
    }

    private void finishSwipe() {
        if (swipeRow != null) {
            swipeRow.settleAfterDrag();
        }
        swipeRow = null;
        state = STATE_REST;
    }

    /**
     * Hands a copy of the event to the list as a cancel, which is what a parent does when it takes a
     * gesture away from a child.
     */
    private void passCancelToSuper(@NonNull MotionEvent ev) {
        MotionEvent cancel = MotionEvent.obtain(ev);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.onTouchEvent(cancel);
        cancel.recycle();
    }

    @Nullable
    private SwipeRevealRow findRowAt(float x, float y) {
        int position = pointToPosition((int) x, (int) y);
        if (position == INVALID_POSITION) {
            return null;
        }
        View row = getChildAt(position - getFirstVisiblePosition());
        return row instanceof SwipeRevealRow ? (SwipeRevealRow) row : null;
    }

    private boolean isHorizontalDrag(@NonNull MotionEvent ev) {
        float dx = Math.abs(ev.getX() - downX);
        float dy = Math.abs(ev.getY() - downY);
        return dx > touchSlop && dx > dy;
    }

    private boolean isVerticalDrag(@NonNull MotionEvent ev) {
        return Math.abs(ev.getY() - downY) > touchSlop;
    }

    private boolean isTap(@NonNull MotionEvent ev) {
        return Math.abs(ev.getX() - downX) <= touchSlop && Math.abs(ev.getY() - downY) <= touchSlop;
    }
}
