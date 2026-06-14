const fs = require('fs');
const path = require('path');

const fontDir = path.join(__dirname, 'app', 'src', 'main', 'res', 'font');

function traverseAndMove(dir) {
    const files = fs.readdirSync(dir);
    for (const file of files) {
        const fullPath = path.join(dir, file);
        const stat = fs.statSync(fullPath);
        if (stat.isDirectory()) {
            traverseAndMove(fullPath);
            fs.rmdirSync(fullPath);
        } else if (file.endsWith('.otf') || file.endsWith('.ttf')) {
            const newName = file.toLowerCase().replace(/[^a-z0-9_]/g, '')
                 .replace('ttf', '.ttf').replace('otf', '.otf');
            
            // avoid dupes, prefer ttf over otf maybe, let's just make valid names
            // actually since we stripped dots, let's fix it:
            let ext = path.extname(file).toLowerCase();
            let base = path.basename(file, path.extname(file)).toLowerCase().replace(/[^a-z0-9_]/g, '_');
            const dest = path.join(fontDir, base + ext);
            fs.renameSync(fullPath, dest);
            console.log(`Moved ${fullPath} to ${dest}`);
        } else {
            console.log(`Ignoring ${fullPath}`);
            fs.unlinkSync(fullPath);
        }
    }
}

traverseAndMove(fontDir);
