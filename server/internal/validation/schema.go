package validation

import (
	"encoding/json"
	"fmt"
	"reflect"
	"strings"

	"github.com/boyd/pocket_agent/server/internal/errors"
)

// Schema represents a JSON schema for validation
type Schema struct {
	Type       string                 `json:"type,omitempty"`
	Properties map[string]Schema      `json:"properties,omitempty"`
	Required   []string               `json:"required,omitempty"`
	Items      *Schema                `json:"items,omitempty"`
	MinLength  *int                   `json:"minLength,omitempty"`
	MaxLength  *int                   `json:"maxLength,omitempty"`
	Minimum    *float64               `json:"minimum,omitempty"`
	Maximum    *float64               `json:"maximum,omitempty"`
	Pattern    string                 `json:"pattern,omitempty"`
	Enum       []interface{}          `json:"enum,omitempty"`
	Format     string                 `json:"format,omitempty"`
	Additional map[string]interface{} `json:"additionalProperties,omitempty"`
}

// MessageSchemas contains predefined schemas for WebSocket messages
var MessageSchemas = map[string]Schema{
	"execute": {
		Type: "object",
		Properties: map[string]Schema{
			"prompt": {
				Type:      "string",
				MinLength: intPtr(1),
				MaxLength: intPtr(100000),
			},
			"options": {
				Type: "object",
				Properties: map[string]Schema{
					"dangerously_skip_permissions": {Type: "boolean"},
					"allowed_tools":                {Type: "array", Items: &Schema{Type: "string"}},
					"disallowed_tools":             {Type: "array", Items: &Schema{Type: "string"}},
					"mcp_config":                   {Type: "string"},
					"append_system_prompt":         {Type: "string"},
					"permission_mode":              {Type: "string", Enum: []interface{}{"", "auto", "always", "never"}},
					"model":                        {Type: "string"},
					"fallback_model":               {Type: "string"},
					"add_dirs":                     {Type: "array", Items: &Schema{Type: "string"}},
					"strict_mcp_config":            {Type: "boolean"},
				},
			},
		},
		Required: []string{"prompt"},
	},
	"project_create": {
		Type: "object",
		Properties: map[string]Schema{
			"path": {
				Type:      "string",
				MinLength: intPtr(1),
				MaxLength: intPtr(4096),
			},
		},
		Required: []string{"path"},
	},
	"project_join": {
		Type: "object",
		Properties: map[string]Schema{
			"project_id": {
				Type:      "string",
				MinLength: intPtr(36),
				MaxLength: intPtr(36),
			},
		},
		Required: []string{"project_id"},
	},
	"get_messages": {
		Type: "object",
		Properties: map[string]Schema{
			"since": {
				Type:   "string",
				Format: "date-time",
			},
			"limit": {
				Type:    "integer",
				Minimum: float64Ptr(1),
				Maximum: float64Ptr(1000),
			},
		},
	},
}

// JSONValidator provides JSON schema validation
type JSONValidator struct {
	schemas map[string]Schema
}

// NewJSONValidator creates a new JSON validator
func NewJSONValidator() *JSONValidator {
	return &JSONValidator{
		schemas: MessageSchemas,
	}
}

// ValidateMessage validates a message against its schema
func (jv *JSONValidator) ValidateMessage(messageType string, data json.RawMessage) error {
	schema, ok := jv.schemas[messageType]
	if !ok {
		// No schema defined, skip validation
		return nil
	}

	var value interface{}
	if err := json.Unmarshal(data, &value); err != nil {
		return errors.NewJSONParsingError(err)
	}

	return jv.validateValue(value, schema, messageType)
}

// validateValue validates a value against a schema
func (jv *JSONValidator) validateValue(value interface{}, schema Schema, path string) error {
	// Check type
	if schema.Type != "" {
		if !jv.checkType(value, schema.Type) {
			return errors.NewValidationError("%s: expected type %s, got %T (value: %v)", path, schema.Type, value, value)
		}
	}

	// Type-specific validation
	switch schema.Type {
	case "object":
		return jv.validateObject(value, schema, path)
	case "array":
		return jv.validateArray(value, schema, path)
	case "string":
		return jv.validateString(value, schema, path)
	case "number", "integer":
		return jv.validateNumber(value, schema, path)
	case "boolean":
		// Boolean doesn't need additional validation
		return nil
	}

	// Check enum values
	if len(schema.Enum) > 0 {
		valid := false
		for _, enumVal := range schema.Enum {
			if reflect.DeepEqual(value, enumVal) {
				valid = true
				break
			}
		}
		if !valid {
			return errors.NewValidationError("%s: value '%v' not in allowed enum values: %v", path, value, schema.Enum)
		}
	}

	return nil
}

// validateObject validates an object against a schema
func (jv *JSONValidator) validateObject(value interface{}, schema Schema, path string) error {
	obj, ok := value.(map[string]interface{})
	if !ok {
		return errors.NewValidationError("%s: expected object, got %T", path, value)
	}

	// Check required properties
	for _, req := range schema.Required {
		if _, exists := obj[req]; !exists {
			return errors.NewValidationError("%s: missing required property '%s'. Object has properties: %v", path, req, getObjectKeys(obj))
		}
	}

	// Validate properties
	for propName, propSchema := range schema.Properties {
		if propValue, exists := obj[propName]; exists {
			propPath := fmt.Sprintf("%s.%s", path, propName)
			if err := jv.validateValue(propValue, propSchema, propPath); err != nil {
				return err
			}
		}
	}

	// Check for additional properties if not allowed
	if addProps, ok := schema.Additional["additionalProperties"]; ok {
		if allowed, ok := addProps.(bool); ok && !allowed {
			for propName := range obj {
				if _, defined := schema.Properties[propName]; !defined {
					return errors.NewValidationError("%s: additional property '%s' not allowed. Allowed properties: %v", path, propName, getSchemaPropertyNames(schema))
				}
			}
		}
	}

	return nil
}

// validateArray validates an array against a schema
func (jv *JSONValidator) validateArray(value interface{}, schema Schema, path string) error {
	arr, ok := value.([]interface{})
	if !ok {
		return errors.NewValidationError("%s: expected array, got %T", path, value)
	}

	// Validate items if schema defined
	if schema.Items != nil {
		for i, item := range arr {
			itemPath := fmt.Sprintf("%s[%d]", path, i)
			if err := jv.validateValue(item, *schema.Items, itemPath); err != nil {
				return err
			}
		}
	}

	return nil
}

// validateString validates a string against a schema
func (jv *JSONValidator) validateString(value interface{}, schema Schema, path string) error {
	str, ok := value.(string)
	if !ok {
		return errors.NewValidationError("%s: expected string, got %T", path, value)
	}

	// Check length constraints
	if schema.MinLength != nil && len(str) < *schema.MinLength {
		return errors.NewValidationError("%s: string length %d is less than minimum %d", path, len(str), *schema.MinLength)
	}

	if schema.MaxLength != nil && len(str) > *schema.MaxLength {
		return errors.NewValidationError("%s: string length %d exceeds maximum %d", path, len(str), *schema.MaxLength)
	}

	// Check pattern if specified
	if schema.Pattern != "" {
		// Pattern validation would go here
		// For now, we'll skip regex validation
	}

	// Check format
	switch schema.Format {
	case "date-time":
		// Validate ISO 8601 date-time format
		// Simple check for now
		if !strings.Contains(str, "T") && !strings.Contains(str, " ") {
			return errors.NewValidationError("%s: invalid date-time format. Expected ISO 8601 format (e.g., '2024-01-01T00:00:00Z'), got: '%s'", path, str)
		}
	}

	return nil
}

// validateNumber validates a number against a schema
func (jv *JSONValidator) validateNumber(value interface{}, schema Schema, path string) error {
	var num float64

	switch v := value.(type) {
	case float64:
		num = v
	case int:
		num = float64(v)
	case int64:
		num = float64(v)
	default:
		return errors.NewValidationError("%s: expected number, got %T", path, value)
	}

	// Check range constraints
	if schema.Minimum != nil && num < *schema.Minimum {
		return errors.NewValidationError("%s: value %f is less than minimum %f", path, num, *schema.Minimum)
	}

	if schema.Maximum != nil && num > *schema.Maximum {
		return errors.NewValidationError("%s: value %f exceeds maximum %f", path, num, *schema.Maximum)
	}

	// For integer type, check if it's a whole number
	if schema.Type == "integer" && num != float64(int64(num)) {
		return errors.NewValidationError("%s: expected integer, got float", path)
	}

	return nil
}

// checkType checks if a value matches the expected type
func (jv *JSONValidator) checkType(value interface{}, expectedType string) bool {
	switch expectedType {
	case "object":
		_, ok := value.(map[string]interface{})
		return ok
	case "array":
		_, ok := value.([]interface{})
		return ok
	case "string":
		_, ok := value.(string)
		return ok
	case "number":
		switch value.(type) {
		case float64, int, int64:
			return true
		}
		return false
	case "integer":
		switch v := value.(type) {
		case int, int64:
			return true
		case float64:
			return v == float64(int64(v))
		}
		return false
	case "boolean":
		_, ok := value.(bool)
		return ok
	case "null":
		return value == nil
	}
	return false
}

// Helper functions
func intPtr(i int) *int {
	return &i
}

func float64Ptr(f float64) *float64 {
	return &f
}

// getObjectKeys returns the keys of an object for error messages
func getObjectKeys(obj map[string]interface{}) []string {
	keys := make([]string, 0, len(obj))
	for k := range obj {
		keys = append(keys, k)
	}
	return keys
}

// getSchemaPropertyNames returns the allowed property names from a schema
func getSchemaPropertyNames(schema Schema) []string {
	names := make([]string, 0, len(schema.Properties))
	for name := range schema.Properties {
		names = append(names, name)
	}
	return names
}
