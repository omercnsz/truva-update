# Xray Go Module

This directory contains the Go source for integrating the Xray-core proxy engine via gomobile.

## Usage

- `go build` builds the Go package normally.
- `./build.sh` runs gomobile bind to produce an `xray.aar` file that you can include in the Android app.

The generated `.aar` will be placed in `app/libs/xray.aar`.

The Go file defines real implementations for `Init`, `SetTunFD` (and a
legacy `ProcessPacket` stub) which bootstrap a gVisor netstack and connect
it to a local Xray instance. Packets are injected through a TUN file
descriptor; both **TCP and UDP** flows are captured by forwarders and
bridged into an Xray dokodemo-door inbound running on localhost. Forwarding
is implemented with gVisor channel endpoints and the `gonet` adapters.

Further steps:

1. Add C headers and libs under `include/` and `lib/` if using a native xray library.
2. Modify code in `xray.go` to call into the library or implement pure Go logic.
3. Run `go mod tidy` and `go build` to verify.
4. Use `build.sh` (requires Android NDK/SDK) to produce the `.aar` for the
   Android project.
