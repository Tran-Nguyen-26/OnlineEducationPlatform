#!/usr/bin/env node
/* eslint-disable no-console */

/**
 * Unified Mock Server Runner for OEP Frontend Development
 * 
 * Features:
 * 1. Automatically discovers and aggregates 100% of OpenAPI contracts (M1–M6) from `../openapi`.
 * 2. Launches Stoplight Prism mock server on port 4010.
 * 3. Supports dynamic error simulation (400, 403, 500) via standard HTTP headers and query params.
 * 4. Watches for file changes in `../openapi` and automatically reloads in real-time.
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import { load, dump } from 'js-yaml';
import chokidar from 'chokidar';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const OPENAPI_DIR = path.resolve(__dirname, '../../openapi');
const OUTPUT_FILE = path.resolve(__dirname, '../.openapi-mock-combined.yaml');
const MOCK_PORT = process.env.MOCK_PORT || 4010;

let prismProcess = null;

function mergeOpenApiSpecs() {
    console.log(`\n🔍 Scanning OpenAPI specs in: ${OPENAPI_DIR}`);
    if (!fs.existsSync(OPENAPI_DIR)) {
        console.error(`❌ Directory not found: ${OPENAPI_DIR}`);
        return false;
    }

    const files = fs.readdirSync(OPENAPI_DIR).filter(f => f.endsWith('.yaml') || f.endsWith('.yml'));
    if (files.length === 0) {
        console.warn(`⚠️ No OpenAPI files found in ${OPENAPI_DIR}`);
        return false;
    }

    console.log(`📦 Found ${files.length} OpenAPI specification(s):`);
    files.forEach(f => console.log(`   - ${f}`));

    const merged = {
        openapi: '3.0.3',
        info: {
            title: 'Online Education Platform - Unified Mock API Gateway',
            version: '1.0.0-mock',
            description: 'Aggregated mock endpoints from all platform modules (M1–M6) for frontend local development.'
        },
        servers: [
            {
                url: `http://localhost:${MOCK_PORT}`,
                description: 'Local Mock Gateway Server'
            }
        ],
        paths: {},
        components: {
            schemas: {},
            securitySchemes: {
                BearerAuth: {
                    type: 'http',
                    scheme: 'bearer',
                    bearerFormat: 'JWT'
                }
            },
            responses: {}
        }
    };

    for (const file of files) {
        const filePath = path.join(OPENAPI_DIR, file);
        try {
            const content = fs.readFileSync(filePath, 'utf-8');
            const doc = load(content);
            if (!doc) continue;

            // Merge paths with collision detection
            if (doc.paths) {
                for (const [route, methods] of Object.entries(doc.paths)) {
                    if (!merged.paths[route]) {
                        merged.paths[route] = { ...methods };
                    } else {
                        for (const [method, def] of Object.entries(methods)) {
                            if (merged.paths[route][method]) {
                                console.warn(`   ⚠️ Conflict detected: ${method.toUpperCase()} ${route} in ${file} overrides previous specification`);
                            }
                            merged.paths[route][method] = def;
                        }
                    }
                }
            }

            // Merge all components (schemas, parameters, responses, securitySchemes, etc.)
            if (doc.components) {
                for (const [compKey, compVal] of Object.entries(doc.components)) {
                    if (!merged.components[compKey]) {
                        merged.components[compKey] = {};
                    }
                    Object.assign(merged.components[compKey], compVal);
                }
            }
        } catch (err) {
            console.error(`❌ Error parsing ${file}:`, err.message);
        }
    }

    // Write merged specification
    const yamlString = dump(merged, { noRefs: true, lineWidth: -1 });
    fs.writeFileSync(OUTPUT_FILE, yamlString, 'utf-8');
    console.log(`✅ Combined ${Object.keys(merged.paths).length} endpoints into ${path.basename(OUTPUT_FILE)}\n`);
    return true;
}

function startPrism() {
    if (prismProcess) {
        prismProcess.kill();
        prismProcess = null;
    }

    const prismBin = path.resolve(__dirname, '../node_modules/.bin/prism');
    const args = ['mock', OUTPUT_FILE, '--port', String(MOCK_PORT), '--host', '0.0.0.0', '--errors'];

    console.log(`🚀 Starting Prism Mock Server on port ${MOCK_PORT}...`);
    console.log(`🌐 Base URL: http://localhost:${MOCK_PORT}`);
    console.log(`\n💡 Error Simulation Instructions for Frontend Devs:`);
    console.log(`   - 400 Bad Request:        Send header 'Prefer: code=400' or query '?__status=400'`);
    console.log(`   - 403 Forbidden:          Send header 'Prefer: code=403' or query '?__status=403'`);
    console.log(`   - 500 Internal Error:     Send header 'Prefer: code=500' or query '?__status=500'\n`);

    prismProcess = spawn(prismBin, args, { stdio: 'inherit' });

    prismProcess.on('error', (err) => {
        console.error('❌ Failed to run Prism:', err);
    });

    prismProcess.on('exit', (code) => {
        if (code !== null && code !== 0) {
            console.log(`Prism process exited with code ${code}`);
        }
    });
}

// Initial build and launch
if (mergeOpenApiSpecs()) {
    startPrism();
}

// Watch mode: Monitor openapi folder for updates
const watcher = chokidar.watch(OPENAPI_DIR, {
    ignoreInitial: true,
    persistent: true
});

watcher.on('all', (event, filePath) => {
    if (filePath.endsWith('.yaml') || filePath.endsWith('.yml')) {
        console.log(`\n🔄 [Watch Mode] OpenAPI file changed (${event}: ${path.basename(filePath)}). Updating mock server...`);
        if (mergeOpenApiSpecs()) {
            startPrism();
        }
    }
});

process.on('SIGINT', () => {
    console.log('\nStopping mock server...');
    if (prismProcess) prismProcess.kill();
    watcher.close();
    process.exit(0);
});
