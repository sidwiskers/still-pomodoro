package app.still.pomodoro;

final class TimerState {
    enum Phase { FOCUS, SHORT_BREAK, LONG_BREAK }

    final Phase phase;
    final boolean running;
    final long endAtMillis;
    final long remainingMillis;
    final int focusInCycle;

    TimerState(Phase phase, boolean running, long endAtMillis, long remainingMillis, int focusInCycle) {
        this.phase = phase;
        this.running = running;
        this.endAtMillis = endAtMillis;
        this.remainingMillis = remainingMillis;
        this.focusInCycle = focusInCycle;
    }
}
