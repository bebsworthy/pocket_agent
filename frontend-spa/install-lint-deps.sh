#!/bin/bash

echo "Installing additional linting and formatting dependencies..."

# Install Prettier and related packages
npm install --save-dev \
  prettier@^3.4.2 \
  prettier-plugin-tailwindcss@^0.6.9

# Install Husky and lint-staged
npm install --save-dev \
  husky@^9.1.7 \
  lint-staged@^15.3.0

# Install additional ESLint plugins
npm install --save-dev \
  eslint-plugin-jsx-a11y@^6.12.1 \
  eslint-plugin-react@^7.42.2 \
  eslint-plugin-import@^2.31.0 \
  eslint-import-resolver-typescript@^3.7.0

echo "Setting up Husky..."
npx husky install

echo "Making pre-commit hook executable..."
chmod +x .husky/pre-commit

echo "Dependencies installed successfully!"
echo ""
echo "You may need to manually update package.json scripts to include:"
echo '  "typecheck": "tsc --noEmit",'
echo '  "format": "prettier --write .",'
echo '  "format:check": "prettier --check .",'
echo '  "prepare": "husky install"'
echo ""
echo "And add the lint-staged configuration to package.json:"
echo '  "lint-staged": {'
echo '    "*.{ts,tsx}": ["eslint --fix", "prettier --write"],'
echo '    "*.{js,jsx,json,css,md}": ["prettier --write"]'
echo '  }'