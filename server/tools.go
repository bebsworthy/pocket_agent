//go:build tools
// +build tools

// Package tools manages tool dependencies for the project.
// These imports ensure that tool dependencies are tracked in go.mod.
package tools

import (
	_ "github.com/golangci/golangci-lint/cmd/golangci-lint"
	_ "github.com/securego/gosec/v2/cmd/gosec"
	_ "golang.org/x/tools/cmd/godoc"
	_ "golang.org/x/vuln/cmd/govulncheck"
	_ "mvdan.cc/gofumpt"
)
