package ru.gwynerva.nuc;

import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

public class CircleAnimator {
    private final View circleView;
    private AnimatorSet pulseAnimator;
    private ValueAnimator colorAnimator;
    private boolean isPulsing = false;

    // Define colors for each state
    private static final int COLOR_ACTIVE = Color.parseColor("#4CAF50");   // Green
    private static final int COLOR_PAUSED = Color.parseColor("#FFC107");   // Yellow/Amber
    private static final int COLOR_STOPPED = Color.parseColor("#F44336"); // Red

    // Current color of the circle
    private int currentColor = COLOR_STOPPED;

    // Single drawable for the circle
    private GradientDrawable circleDrawable;

    public CircleAnimator(View circleView) {
        this.circleView = circleView;

        // Setup the single drawable
        setupCircleDrawable();

        // Setup pulse animation
        setupPulseAnimation();

        // Start pulse animation immediately
        startPulse();
    }

    // Setup the circle drawable
    private void setupCircleDrawable() {
        // Create a new GradientDrawable for the circle
        circleDrawable = new GradientDrawable();
        circleDrawable.setShape(GradientDrawable.OVAL);
        circleDrawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        circleDrawable.setColors(new int[]{ currentColor, 0x00000000 });
        circleDrawable.setGradientCenter(.5f, .5f);
        circleView.post(() -> {
            int size = Math.min(circleView.getWidth(), circleView.getHeight());
            circleDrawable.setGradientRadius(size / 2f);
        });

        // Set the drawable as the background
        circleView.setBackground(circleDrawable);
        circleView.setAlpha(.4f);
    }

    // Setup the pulse animation
    private void setupPulseAnimation() {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(circleView, "scaleX", .8f, 1.1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(circleView, "scaleY", .8f, 1.1f);

        // Make it slow and smooth - 2 seconds per pulse cycle
        scaleX.setDuration(2000);
        scaleY.setDuration(2000);

        // Repeat indefinitely
        scaleX.setRepeatCount(ObjectAnimator.INFINITE);
        scaleY.setRepeatCount(ObjectAnimator.INFINITE);

        // Repeat in reverse for smooth back-and-forth
        scaleX.setRepeatMode(ObjectAnimator.REVERSE);
        scaleY.setRepeatMode(ObjectAnimator.REVERSE);

        // Use a smooth interpolation
        scaleX.setInterpolator(new AccelerateDecelerateInterpolator());
        scaleY.setInterpolator(new AccelerateDecelerateInterpolator());

        // Combine the animations
        pulseAnimator = new AnimatorSet();
        pulseAnimator.playTogether(scaleX, scaleY);
    }

    // Start the pulse animation - ensure it's always running
    public void startPulse() {
        if (!isPulsing && pulseAnimator != null) {
            // Make sure we start from normal scale
            circleView.setScaleX(1f);
            circleView.setScaleY(1f);
            pulseAnimator.start();
            isPulsing = true;
        }
    }

    // Transition to active state with animation
    public void animateToActive() {
        animateColor(COLOR_ACTIVE);
    }

    // Transition to paused state with animation
    public void animateToPaused() {
        animateColor(COLOR_PAUSED);
    }

    // Transition to stopped state with animation
    public void animateToStopped() {
        animateColor(COLOR_STOPPED);
    }

    // Animate color transition
    private void animateColor(int targetColor) {
        // Cancel any ongoing animation
        if (colorAnimator != null && colorAnimator.isRunning()) {
            colorAnimator.cancel();
        }

        // Create color animator
        colorAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), currentColor, targetColor);
        colorAnimator.setDuration(500); // Half second transition
        colorAnimator.addUpdateListener(animator -> {
            int color = (int) animator.getAnimatedValue();
            circleDrawable.setColors(new int[]{ color, 0x00000000 });
        });

        // Start the animation
        colorAnimator.start();

        // Update current color
        currentColor = targetColor;

        // Ensure pulse is running
        startPulse();
    }
}
