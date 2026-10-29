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

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.niendo.ImapNotes3.R;

import java.lang.ref.WeakReference;

/**
 * The row of a {@link SwipeRevealListView}: it slides its content sideways and reveals the action
 * buttons underneath, the way the note lists work in the iOS Notes app.
 * <p>
 * The row layout needs two children with fixed ids:
 * <ul>
 *     <li>{@code @+id/rowActions} the buttons, declared first so they are drawn behind the content</li>
 *     <li>{@code @+id/rowContent} the normal row, opaque, and the part that slides</li>
 * </ul>
 * The sliding itself is driven by {@link SwipeRevealListView}, because only the list sees the whole
 * touch stream: a plain row either gets the events of a clickable child or none at all.
 * <p>
 * At most one row of the whole app is revealed at a time, and a row that the list reuses always
 * starts closed (see {@link #concealImmediately()}), so translated rows cannot survive a scroll or
 * a list refresh.
 */
public class SwipeRevealRow extends FrameLayout {
    private static final String TAG = "IN_SwipeRevealRow";
    private static final int SNAP_ANIMATION_MS = 150;
    // Width used when the action buttons have not been measured yet.
    private static final float FALLBACK_REVEAL_WIDTH_DP = 64f;

    /**
     * The row that is currently slid open. Weak, and global on purpose: the lists of this app never
     * overlap, so one row at a time is enough, and no list has to keep track of positions.
     */
    @Nullable
    private static WeakReference<SwipeRevealRow> revealedRow = null;

    @Nullable
    private View rowContent;
    @Nullable
    private View rowActions;
    private int revealWidth = 0;
    private boolean revealed = false;

    public SwipeRevealRow(@NonNull Context context) {
        super(context);
    }

    public SwipeRevealRow(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SwipeRevealRow(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        findParts();
        // AbsListView only performs the click of an item itself when the row has no explicitly
        // focusable descendant, and the constructor of ImageButton forces setFocusable(true) on our
        // action buttons whatever the layout says. Without this, tapping a row would do nothing.
        // The buttons are still reached by touch, they just cannot take focus.
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
    }

    /**
     * Closes whichever row is currently slid open.
     *
     * @return true if a row was open and has been closed, false if nothing was revealed
     */
    public static boolean concealRevealedRow() {
        if (revealedRow == null) {
            return false;
        }
        SwipeRevealRow row = revealedRow.get();
        revealedRow = null;
        if (row == null) {
            return false;
        }
        row.revealed = false;
        row.slideContentTo(0f);
        return true;
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent ev) {
        if (isTouchCoveredByContent(ev)) {
            // The panel hides under the content here, so its buttons must not be hit. Refusing the
            // event hands the gesture to the list, which turns a tap into an item click and can still
            // recognise a horizontal drag that opens this row.
            return false;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        findParts();
        if (rowContent == null || rowActions == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        // The action panel sits behind the content and is never taller than it. Measuring it with the
        // height spec coming from the list would let a wrap_content row grow to the list's height.
        measureChildWithMargins(rowContent, widthMeasureSpec, 0, heightMeasureSpec, 0);
        rowActions.measure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(rowContent.getMeasuredHeight(), MeasureSpec.EXACTLY));
        setMeasuredDimension(
                resolveSize(Math.max(rowContent.getMeasuredWidth(), rowActions.getMeasuredWidth()),
                        widthMeasureSpec),
                resolveSize(Math.max(rowContent.getMeasuredHeight(), rowActions.getMeasuredHeight()),
                        heightMeasureSpec));
        revealWidth = 0;
    }

    /**
     * Moves the content by {@code dx}, never past the closed and the fully open position. Called for
     * every move of a horizontal drag.
     */
    public void dragContentBy(float dx) {
        if (rowContent == null) {
            return;
        }
        float translation = rowContent.getTranslationX() + dx;
        rowContent.setTranslationX(Math.max(-getRevealWidth(), Math.min(0f, translation)));
    }

    /**
     * Decides at the end of a drag whether the row stays open or snaps back.
     */
    public void settleAfterDrag() {
        if (rowContent == null) {
            return;
        }
        if (rowContent.getTranslationX() <= -getRevealWidth() / 2f) {
            reveal();
        } else {
            concealImmediately();
        }
    }

    /**
     * Puts the row back to its closed state without animating. Called whenever the list binds a row,
     * because a reused row must never still be slid open.
     */
    public void concealImmediately() {
        findParts();
        if (rowContent != null) {
            rowContent.setTranslationX(0f);
        }
        revealed = false;
        forgetIfRevealed(this);
    }

    /**
     * How far the content can slide, which is the width of the action panel.
     */
    public int getRevealWidth() {
        if (revealWidth == 0) {
            if (rowActions != null && rowActions.getWidth() > 0) {
                revealWidth = rowActions.getWidth();
            } else {
                revealWidth = (int) (FALLBACK_REVEAL_WIDTH_DP * getResources().getDisplayMetrics().density);
            }
        }
        return revealWidth;
    }

    private void reveal() {
        SwipeRevealRow previouslyRevealed = revealedRow != null ? revealedRow.get() : null;
        revealedRow = new WeakReference<>(this);
        revealed = true;
        if (previouslyRevealed != null && previouslyRevealed != this) {
            previouslyRevealed.concealImmediately();
        }
        slideContentTo(-getRevealWidth());
    }

    private void slideContentTo(float target) {
        if (rowContent == null) {
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(rowContent.getTranslationX(), target);
        animator.setDuration(SNAP_ANIMATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation ->
                rowContent.setTranslationX((float) animation.getAnimatedValue()));
        animator.start();
    }

    private static void forgetIfRevealed(@NonNull SwipeRevealRow row) {
        if (revealedRow != null && revealedRow.get() == row) {
            revealedRow = null;
        }
    }

    /**
     * Whether the touch of {@code ev} (in this row's coordinates) lands on the part of the action
     * panel that the sliding content still covers, i.e. where its buttons are not visible.
     * <p>
     * The action panel may therefore only be hit where it is actually exposed, which is derived from
     * the same {@code translationX} the swipe animates. It is deliberately not expressed with the
     * clickable flag of the buttons, as one might expect: the adapters set a click listener on them
     * every time they bind a row, and {@link View#setOnClickListener} switches clickability back on,
     * so clearing that flag in {@link #concealImmediately()} is undone a few lines later in {@code
     * getView}/{@code bindView}, and the hidden buttons answer a tap on the closed row - in both the
     * account overview and the note list.
     */
    private boolean isTouchCoveredByContent(@NonNull MotionEvent ev) {
        if (rowActions == null || rowContent == null) {
            return false;
        }
        float x = ev.getX();
        float y = ev.getY();
        if (x < rowActions.getLeft() || x > rowActions.getRight()
                || y < rowActions.getTop() || y > rowActions.getBottom()) {
            // Not on the panel at all; the normal dispatch decides, and the content is not clickable.
            return false;
        }
        // The content spans from the left edge of the row to here, so everything right of it is seen.
        return !revealed || x < rowContent.getWidth() + rowContent.getTranslationX();
    }

    private void findParts() {
        if (rowContent == null) {
            rowContent = findViewById(R.id.rowContent);
        }
        if (rowActions == null) {
            rowActions = findViewById(R.id.rowActions);
        }
    }
}
