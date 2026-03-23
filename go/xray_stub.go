//go:build !linux
// +build !linux

package xray

// This stub file is compiled on non-Linux platforms to avoid gVisor build errors.

// Init is a no-op on unsupported platforms.
func Init(config string) error {
	return nil
}

// ProcessPacket is a no-op stub.
func ProcessPacket(pkt []byte) []byte {
	return nil
}

// SetTunFD stub for unsupported platforms.
func SetTunFD(fd int64) error {
	return nil
}

// TestConnection stub for unsupported platforms.
func TestConnection() string {
	return "FAIL:stub:unsupported_platform"
}

// Stop stub for unsupported platforms.
func Stop() {}
