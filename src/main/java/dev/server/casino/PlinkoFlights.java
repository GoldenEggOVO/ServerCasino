package dev.server.casino;

import java.util.ArrayList;
import java.util.List;

/** Tick-driven flights; one entity per ball and at most five launches per second. */
public final class PlinkoFlights {
    public static final class Flight {
        final int path;
        int frame;

        Flight(int path) {
            this.path = path;
        }

        public int path() {
            return path;
        }

        public int frame() {
            return frame;
        }
    }

    private final List<Flight> flights = new ArrayList<>();
    private int tick;
    private int lastLaunch = -4;

    public Flight launch(int path) {
        if (flights.size() >= 25 || tick - lastLaunch < 4) {
            return null;
        }
        Flight flight = new Flight(path);
        flights.add(flight);
        lastLaunch = tick;
        return flight;
    }

    public List<Flight> active() {
        return List.copyOf(flights);
    }

    public List<Flight> advance() {
        tick++;
        List<Flight> ended = new ArrayList<>();
        flights.removeIf(
                flight -> {
                    if (++flight.frame <= 96) {
                        return false;
                    }
                    ended.add(flight);
                    return true;
                });
        return ended;
    }
}
