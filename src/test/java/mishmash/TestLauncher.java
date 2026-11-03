package mishmash;

/**
 * Gives integration tests access to the isolated launcher without widening the application API.
 */
public final class TestLauncher {
    private TestLauncher() {
    }

    /**
     * Runs a session without loading or saving the user's persistent data.
     *
     * @param logPattern The rotating log destination.
     */
    public static void runMain(String logPattern) {
        MishMash.runMain(logPattern);
    }
}
