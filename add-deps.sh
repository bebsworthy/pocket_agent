#!/bin/bash
# Install missing dependencies identified in RW-B code review fixes

echo "Installing missing dependencies for frontend-spa..."
cd /Users/boyd/wip/pocket_agent/frontend-spa

echo "Installing clsx and tailwind-merge..."
npm install clsx@^2.1.1 tailwind-merge@^2.5.4

echo "Running type check..."
npm run type-check

echo "Running linter..."  
npm run lint

echo "RW-B dependency installation complete!"
echo "See /Users/boyd/wip/pocket_agent/frontend-spa/RW-B-FIXES.md for details"