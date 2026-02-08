# iOS Project Bootstrap (`bd-1jv`)

This directory contains the initial iOS app scaffold for `MarrakechGuide`.

## Structure

- `MarrakechGuide/`: app source, resources, and DI container
- `MarrakechGuideTests/`: unit test target placeholders
- `MarrakechGuideUITests/`: UI test target placeholders
- `project.yml`: XcodeGen specification, including GRDB package dependency

## Generate the Xcode Project

On macOS with Xcode installed:

```bash
brew install xcodegen
cd ios/MarrakechGuide
xcodegen generate
```

This creates `MarrakechGuide.xcodeproj` from `project.yml`.

## Build Check (on macOS)

```bash
xcodebuild build -scheme MarrakechGuide -destination 'generic/platform=iOS'
```
