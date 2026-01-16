package site.zetx.cloudflyer.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * CFSolver CLI - Command line tool for CloudFlyer API.
 * 
 * <p>Usage:</p>
 * <pre>
 *   cfsolver solve cloudflare URL
 *   cfsolver solve turnstile URL SITEKEY
 *   cfsolver request URL
 *   cfsolver balance
 * </pre>
 */
@Command(
    name = "cfsolver",
    description = "CFSolver - Cloudflare challenge solver using cloud API",
    version = "0.2.0",
    mixinStandardHelpOptions = true,
    subcommands = {
        SolveCommand.class,
        RequestCommand.class,
        BalanceCommand.class
    }
)
public class CFSolverCLI implements Runnable {
    
    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output", scope = CommandLine.ScopeType.INHERIT)
    static boolean verbose;
    
    @Option(names = {"-K", "--api-key"}, description = "API key (or set CLOUDFLYER_API_KEY env var)", scope = CommandLine.ScopeType.INHERIT)
    static String apiKey;
    
    @Option(names = {"-B", "--api-base"}, description = "API base URL (default: https://solver.zetx.site)", scope = CommandLine.ScopeType.INHERIT)
    static String apiBase;
    
    public static void main(String[] args) {
        int exitCode = new CommandLine(new CFSolverCLI())
                .setExecutionExceptionHandler(new ExceptionHandler())
                .execute(args);
        System.exit(exitCode);
    }
    
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
    
    static String getApiKey() {
        if (apiKey != null && !apiKey.isEmpty()) {
            return apiKey;
        }
        String envKey = System.getenv("CLOUDFLYER_API_KEY");
        if (envKey != null && !envKey.isEmpty()) {
            return envKey;
        }
        return null;
    }
    
    static String getApiBase() {
        if (apiBase != null && !apiBase.isEmpty()) {
            return apiBase;
        }
        String envBase = System.getenv("CLOUDFLYER_API_BASE");
        if (envBase != null && !envBase.isEmpty()) {
            return envBase;
        }
        return "https://solver.zetx.site";
    }
    
    static void requireApiKey() {
        if (getApiKey() == null) {
            System.err.println("Error: API key required. Use -K/--api-key or set CLOUDFLYER_API_KEY environment variable.");
            System.exit(1);
        }
    }
    
    static void verboseLog(String message) {
        if (verbose) {
            System.out.println(message);
        }
    }
    
    static class ExceptionHandler implements CommandLine.IExecutionExceptionHandler {
        @Override
        public int handleExecutionException(Exception ex, CommandLine cmd, CommandLine.ParseResult parseResult) {
            System.err.println("[x] Error: " + ex.getMessage());
            if (verbose) {
                ex.printStackTrace();
            }
            return 1;
        }
    }
}
