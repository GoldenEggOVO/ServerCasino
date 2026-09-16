package dev.server.casino;

import static org.junit.jupiter.api.Assertions.*;

import dev.server.casino.api.EconomyProvider;
import dev.server.casino.economy.EconomyAccess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

class EconomyAccessTest {
    @TempDir Path dir;

    static class Provider implements EconomyProvider {
        Result result = Result.SUCCESS;
        int debits;

        public boolean available() {
            return true;
        }

        public OptionalLong balance(UUID player) {
            return OptionalLong.of(1200);
        }

        public Result withdraw(UUID player, long cents) {
            debits++;
            return result;
        }

        public Result deposit(UUID player, long cents) {
            return result;
        }
    }

    @Test
    void providerUnloadIsSeenAndUnknownNeverReportsSuccess() {
        Provider provider = new Provider();
        var current = new AtomicReference<EconomyProvider>(provider);
        EconomyAccess access = new EconomyAccess(current::get);
        UUID player = UUID.randomUUID();
        assertTrue(access.take(player, 100));
        provider.result = EconomyProvider.Result.DECLINED;
        assertFalse(access.take(player, 100));
        provider.result = EconomyProvider.Result.UNKNOWN;
        assertThrows(IllegalStateException.class, () -> access.take(player, 100));
        current.set(null);
        assertFalse(access.available());
        assertTrue(access.balance(player).isEmpty());
        assertThrows(IllegalStateException.class, () -> access.give(player, 100));
        assertEquals(3, provider.debits);
    }

    @Test
    void unknownPublicProviderResultSurvivesRestartForReconciliation() throws Exception {
        Provider provider = new Provider();
        provider.result = EconomyProvider.Result.UNKNOWN;
        EconomyAccess access = new EconomyAccess(() -> provider);
        MinesService.Wallet wallet =
                new MinesService.Wallet() {
                    public boolean available() {
                        return access.available();
                    }

                    public boolean take(UUID player, long cents) {
                        return access.take(player, cents);
                    }

                    public boolean give(UUID player, long cents) {
                        return access.give(player, cents);
                    }
                };
        UUID player = UUID.randomUUID();
        var service = new MinesService(new RoundStore(dir), wallet);
        assertThrows(IllegalStateException.class, () -> service.start(player, 100, 3, false));
        var restarted = new MinesService(new RoundStore(dir), wallet);
        assertEquals(MinesRound.Phase.DEBIT_PENDING, restarted.get(player).phase);
        assertThrows(IllegalStateException.class, () -> restarted.start(player, 100, 3, false));
        assertEquals(1, provider.debits);
    }
}
