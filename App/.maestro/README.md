# Maestro UI Tests for Wizly

This directory contains Maestro UI tests for the Wizly mobile application.

## Installation

### macOS
```bash
brew tap mobile-dev-inc/tap
brew install maestro
```

### Linux
```bash
curl -Ls "https://get.maestro.mobile.dev" | bash
export PATH="$PATH:$HOME/.maestro/bin"
```

### Windows
Use WSL2 and follow Linux instructions.

### Verify Installation
```bash
maestro --version
```

## Running Tests

### Android

1. Start an Android emulator:
```bash
emulator -avd Pixel_7_API_34
```

2. Run a single test:
```bash
maestro test .maestro/login_flow.yaml
```

3. Run all tests:
```bash
maestro test .maestro/
```

### iOS

1. Start iOS Simulator:
```bash
open -a Simulator
```

2. Run a single test:
```bash
maestro test .maestro/login_flow.yaml
```

3. Run all tests:
```bash
maestro test .maestro/
```

## Test Files

| Test File | Description | Duration | Prerequisites |
|-----------|-------------|----------|---------------|
| `login_flow.yaml` | Google Sign-In authentication | ~30s | Test Google account |
| `check_my_work.yaml` | Scan your work and get hint-first feedback | ~2 min | Logged in user with a course; server with a Gemini key |
| `navigation_test.yaml` | Bottom navigation tabs and the + sheet | ~30s | Logged in user |

## Running Tests in Sequence

To run tests that depend on login, use Maestro's built-in flow mechanism:

```yaml
# Example: Run login first, then scan
maestro test .maestro/login_flow.yaml .maestro/check_my_work.yaml
```

## Cloud Testing (Optional)

Maestro Cloud provides 100 free tests per month.

### Sign Up
```bash
maestro cloud signup
```

### Upload and Run
```bash
# Android
maestro cloud .maestro/ \
  --app-file ../androidApp/build/outputs/apk/debug/app-debug.apk

# iOS
maestro cloud .maestro/ \
  --app-file ../iosApp/build/iosApp.app
```

## Tips for Writing Tests

1. **Use regex for flexibility**:
   ```yaml
   - assertVisible:
       text: "Welcome.*"
       regex: true
   ```

2. **Add timeouts for slow operations**:
   ```yaml
   - assertVisible:
       text: "Results"
       timeout: 30000  # 30 seconds
   ```

3. **Make elements optional when needed**:
   ```yaml
   - tapOn:
       text: "Skip"
       optional: true
   ```

4. **Take screenshots for debugging**:
   ```yaml
   - takeScreenshot: "screen-name"
   ```

5. **Use back navigation**:
   ```yaml
   - back
   ```

## Debugging Failed Tests

1. **Run with video recording**:
   ```bash
   maestro test --record .maestro/login_flow.yaml
   ```

2. **Run in debug mode**:
   ```bash
   maestro test --debug .maestro/login_flow.yaml
   ```

3. **View maestro studio**:
   ```bash
   maestro studio
   ```

## CI/CD Integration

Add to GitHub Actions workflow:

```yaml
- name: Install Maestro
  run: |
    brew tap mobile-dev-inc/tap
    brew install maestro

- name: Run Maestro Tests
  run: |
    maestro test Wizly-App/.maestro/
```

## Test Maintenance

- Update selectors when UI changes
- Keep tests focused on critical user journeys
- Run tests before each PR
- Update screenshots in documentation

## Support

- Maestro Docs: https://maestro.mobile.dev
- Maestro Community: https://discord.gg/maestro
- GitHub Issues: https://github.com/mobile-dev-inc/maestro/issues
