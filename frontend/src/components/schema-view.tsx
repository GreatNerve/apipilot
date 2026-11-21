"use client";

import { useState, type ReactNode } from "react";
import type { JsonSchema } from "@/lib/api";

type Schemas = Record<string, JsonSchema>;

const CONSTRAINTS = [
  "minLength",
  "maxLength",
  "minimum",
  "maximum",
  "exclusiveMinimum",
  "exclusiveMaximum",
  "pattern",
  "minItems",
  "maxItems",
] as const;

const COMPOSITIONS = ["allOf", "oneOf", "anyOf"] as const;

function refName(schema: JsonSchema): string | undefined {
  const ref = schema.$ref;
  return typeof ref === "string" ? ref.slice(ref.lastIndexOf("/") + 1) : undefined;
}

/** Renders a JSON Schema as a property list. References stay collapsed until clicked. */
export function SchemaView({ schema, schemas }: { schema: JsonSchema; schemas: Schemas }) {
  const name = refName(schema);
  const resolved = name ? schemas[name] : schema;
  if (!resolved) {
    return <p className="text-sm text-muted-foreground">Unknown schema {name}</p>;
  }
  return <SchemaBody schema={resolved} schemas={schemas} />;
}

function SchemaBody({ schema, schemas }: { schema: JsonSchema; schemas: Schemas }) {
  const properties = schema.properties as Record<string, JsonSchema> | undefined;
  const required = new Set((schema.required as string[] | undefined) ?? []);
  const composition = COMPOSITIONS.find((keyword) => Array.isArray(schema[keyword]));

  return (
    <div className="flex flex-col gap-1 text-sm">
      {composition && (
        <div>
          <span className="text-xs text-muted-foreground">{composition}</span>
          <ul className="ml-3 border-l pl-3">
            {(schema[composition] as JsonSchema[]).map((member, index) => (
              <li key={index}>
                <TypeLabel schema={member} schemas={schemas} />
              </li>
            ))}
          </ul>
        </div>
      )}
      {properties ? (
        <ul className="flex flex-col">
          {Object.entries(properties).map(([name, property]) => (
            <li key={name} data-testid={`prop-${name}`} className="border-b py-1.5 last:border-0">
              <div className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
                <code className="font-mono text-[0.8rem] font-medium">{name}</code>
                <TypeLabel schema={property} schemas={schemas} />
                {required.has(name) && <span className="text-xs text-amber-600">required</span>}
                {constraintsOf(property).map((constraint) => (
                  <span key={constraint} className="text-xs text-muted-foreground">
                    {constraint}
                  </span>
                ))}
              </div>
            </li>
          ))}
        </ul>
      ) : (
        !composition && <TypeLabel schema={schema} schemas={schemas} />
      )}
    </div>
  );
}

function TypeLabel({ schema, schemas }: { schema: JsonSchema; schemas: Schemas }): ReactNode {
  const name = refName(schema);
  if (name) {
    return <Reference name={name} schemas={schemas} />;
  }
  if (schema.type === "array" && schema.items) {
    return (
      <span className="inline-flex flex-wrap items-baseline gap-1">
        <span className="text-muted-foreground">array of</span>
        <TypeLabel schema={schema.items as JsonSchema} schemas={schemas} />
      </span>
    );
  }
  if (schema.properties || COMPOSITIONS.some((keyword) => Array.isArray(schema[keyword]))) {
    return (
      <div className="ml-3 w-full border-l pl-3">
        <SchemaBody schema={schema} schemas={schemas} />
      </div>
    );
  }
  if (Array.isArray(schema.enum)) {
    return <span className="text-muted-foreground">enum: {schema.enum.join(" | ")}</span>;
  }
  const type = Array.isArray(schema.type) ? schema.type.join(" | ") : (schema.type ?? "any");
  const format = typeof schema.format === "string" ? ` (${schema.format})` : "";
  return <span className="text-muted-foreground">{`${type}${format}`}</span>;
}

function Reference({ name, schemas }: { name: string; schemas: Schemas }) {
  const [open, setOpen] = useState(false);
  const target = schemas[name];
  return (
    <>
      <button
        type="button"
        aria-expanded={open}
        onClick={() => setOpen(!open)}
        className="rounded bg-muted px-1.5 font-mono text-[0.8rem] hover:bg-accent"
      >
        {name}
      </button>
      {open && target && (
        <div className="ml-3 w-full border-l pl-3">
          <SchemaBody schema={target} schemas={schemas} />
        </div>
      )}
    </>
  );
}

function constraintsOf(schema: JsonSchema): string[] {
  return CONSTRAINTS.filter((key) => schema[key] !== undefined).map(
    (key) => `${key} ${String(schema[key])}`,
  );
}
