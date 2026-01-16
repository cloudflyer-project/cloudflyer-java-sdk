# CFSolver Java SDK

[![Java SDK CI](https://github.com/cloudflyer-project/cloudflyer-java-sdk/actions/workflows/ci.yml/badge.svg)](https://github.com/cloudflyer-project/cloudflyer-java-sdk/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/site.zetx.cloudflyer/cfsolver.svg)](https://search.maven.org/artifact/site.zetx.cloudflyer/cfsolver)
[![Java 11+](https://img.shields.io/badge/java-11+-blue.svg)](https://www.oracle.com/java/technologies/javase-jdk11-downloads.html)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Java SDK for [CloudFlyer](https://cloudflyer.zetx.site) - automatically bypass Cloudflare challenges and solve Turnstile CAPTCHAs.

**GitHub**: https://github.com/cloudflyer-project/cloudflyer-java-sdk

## Features

- **HTTP Client** - OkHttp-based client with automatic Cloudflare challenge bypass
- **Challenge Detection** - Automatically detects and solves Cloudflare protection
- **Turnstile Solver** - Solve Cloudflare Turnstile CAPTCHA and retrieve tokens
- **CLI Tool** - Command-line interface for quick testing and scripting
- **Proxy Support** - HTTP/HTTPS proxy for requests and API calls
- **MaskTunnel** - Optional TLS fingerprint simulation (JA3/JA4)

## Installation

### Maven

```xml
<dependency>
    <groupId>site.zetx.cloudflyer</groupId>
    <artifactId>cfsolver</artifactId>
    <version>0.2.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'site.zetx.cloudflyer:cfsolver:0.2.0'
```

## Quick Start

```java
import site.zetx.cloudflyer.CloudflareSolver;
import site.zetx.cloudflyer.Response;

public class Example {
    public static void main(String[] args) throws Exception {
        try (CloudflareSolver solver = new CloudflareSolver("your-api-key")) {
            Response response = solver.get("https://protected-site.com");
            System.out.println(response.body());
        }
    }
}
```

## SDK Usage

### Basic Configuration

```java
// Simple initialization
CloudflareSolver solver = new CloudflareSolver(apiKey);

// Builder pattern with options
CloudflareSolver solver = CloudflareSolver.builder(apiKey)
    .apiBase("https://solver.zetx.site")
    .solve(true)              // Enable challenge solving
    .onChallenge(true)        // Solve only when challenge detected
    .timeout(30000)           // Request timeout (ms)
    .proxy("http://host:port")
    .apiProxy("http://api-proxy:port")
    .useMaskTunnel(false)     // TLS fingerprint simulation
    .build();
```

### HTTP Methods

```java
// GET
Response response = solver.get(url);
Response response = solver.get(url, headers);

// POST
Response response = solver.post(url, body);
Response response = solver.post(url, body, headers);

// PUT / DELETE / PATCH
Response response = solver.put(url, body, headers);
Response response = solver.delete(url, headers);
Response response = solver.patch(url, body, headers);

// Generic request
Response response = solver.request("GET", url, body, headers);
```

### Turnstile CAPTCHA

```java
// Solve Turnstile and get token
String token = solver.solveTurnstile(
    "https://example.com/page",
    "0x4AAAAAAA..."  // sitekey
);

// Use token in form submission
Map<String, String> form = new HashMap<>();
form.put("cf-turnstile-response", token);
Response response = solver.post("https://example.com/submit", form);
```

### Proxy Configuration

```java
// Proxy for HTTP requests
CloudflareSolver solver = CloudflareSolver.builder(apiKey)
    .proxy("http://host:port")
    .build();

// Proxy with authentication
CloudflareSolver solver = CloudflareSolver.builder(apiKey)
    .proxy("http://host:port", "username", "password")
    .build();

// Separate proxy for API calls
CloudflareSolver solver = CloudflareSolver.builder(apiKey)
    .proxy("http://request-proxy:port")
    .apiProxy("http://api-proxy:port")
    .build();
```

### Solving Modes

```java
// On-demand: solve only when challenge detected (default)
CloudflareSolver solver = CloudflareSolver.builder(apiKey)
    .onChallenge(true)
    .build();

// Pre-solve: always solve before request
CloudflareSolver solver = CloudflareSolver.builder(apiKey)
    .onChallenge(false)
    .build();

// Disable solving
CloudflareSolver solver = CloudflareSolver.builder(apiKey)
    .solve(false)
    .build();
```

### Error Handling

```java
import site.zetx.cloudflyer.exceptions.*;

try (CloudflareSolver solver = new CloudflareSolver(apiKey)) {
    Response response = solver.get("https://protected-site.com");
} catch (CFSolverTimeoutException e) {
    // Challenge solving timed out
} catch (CFSolverChallengeException e) {
    // Failed to solve challenge
} catch (CFSolverConnectionException e) {
    // Network connection error
} catch (CFSolverAPIException e) {
    // API error
} catch (CFSolverException e) {
    // General error
}
```

## CLI Usage

The SDK includes a command-line tool for quick testing.

### Environment Variables

```bash
set CLOUDFLYER_API_KEY=your_api_key
set CLOUDFLYER_API_BASE=https://solver.zetx.site  # optional
```

### Commands

```bash
# Show help
mvn exec:java -Dexec.mainClass="site.zetx.cloudflyer.cli.CFSolverCLI" -Dexec.args="--help"

# Solve Cloudflare challenge
mvn exec:java -Dexec.mainClass="site.zetx.cloudflyer.cli.CFSolverCLI" \
    -Dexec.args="solve cloudflare https://example.com"

# Solve Turnstile CAPTCHA
mvn exec:java -Dexec.mainClass="site.zetx.cloudflyer.cli.CFSolverCLI" \
    -Dexec.args="solve turnstile https://example.com 0x4AAAAAAA..."

# Make HTTP request with auto bypass
mvn exec:java -Dexec.mainClass="site.zetx.cloudflyer.cli.CFSolverCLI" \
    -Dexec.args="request https://protected-site.com"

# Check account balance
mvn exec:java -Dexec.mainClass="site.zetx.cloudflyer.cli.CFSolverCLI" \
    -Dexec.args="balance"
```

### CLI Options

```
Global Options:
  -K, --api-key <key>     API key (or use CLOUDFLYER_API_KEY env)
  -B, --api-base <url>    API base URL
  -v, --verbose           Enable verbose output

solve cloudflare <url>:
  -X, --proxy <url>       Proxy for HTTP requests
  --api-proxy <url>       Proxy for API calls
  -T, --timeout <sec>     Timeout in seconds (default: 120)
  --json                  Output as JSON

solve turnstile <url> <sitekey>:
  --api-proxy <url>       Proxy for API calls
  -T, --timeout <sec>     Timeout in seconds (default: 120)
  --json                  Output as JSON

request <url>:
  -X, --proxy <url>       Proxy for HTTP requests
  --api-proxy <url>       Proxy for API calls
  -m, --method <method>   HTTP method (default: GET)
  -d, --data <body>       Request body
  -H, --header <header>   Request header (repeatable)
  -o, --output <file>     Save response to file
  -T, --timeout <sec>     Timeout in seconds (default: 120)
  --masktunnel            Enable TLS fingerprint simulation
  --json                  Output as JSON

balance:
  --api-proxy <url>       Proxy for API calls
  --json                  Output as JSON
```

### CLI Examples

```bash
# With proxy
mvn exec:java -Dexec.mainClass="site.zetx.cloudflyer.cli.CFSolverCLI" \
    -Dexec.args="solve cloudflare https://example.com --proxy http://127.0.0.1:8080"

# POST request with data
mvn exec:java -Dexec.mainClass="site.zetx.cloudflyer.cli.CFSolverCLI" \
    -Dexec.args="request https://api.example.com -m POST -d '{\"key\":\"value\"}'"

# JSON output
mvn exec:java -Dexec.mainClass="site.zetx.cloudflyer.cli.CFSolverCLI" \
    -Dexec.args="balance --json"
```

## Run Examples

```bash
set CLOUDFLYER_API_KEY=your_api_key

# Challenge bypass example
mvn exec:java -Dexec.mainClass="site.zetx.cloudflyer.examples.SdkChallenge"

# Turnstile example
mvn exec:java -Dexec.mainClass="site.zetx.cloudflyer.examples.SdkTurnstile"
```

## Development

### Build

```bash
# Compile
mvn compile

# Package JAR
mvn package

# Package without tests
mvn package -DskipTests
```

### Run Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=ResponseTest

# Run with verbose output
mvn test -X
```

### Code Quality

```bash
# Generate Javadoc
mvn javadoc:javadoc

# Generate sources JAR
mvn source:jar
```

## License

MIT License
