// Run with Node.js and sharp available to regenerate legacy launcher icons.
const fs = require('node:fs');
const path = require('node:path');
const sharp = require('sharp');
const root = path.resolve(__dirname, '..');
const res = path.join(root, 'app/src/main/res');
const foreground = fs.readFileSync(path.join(res, 'drawable-v24/ic_launcher_foreground.xml'), 'utf8');
const background = fs.readFileSync(path.join(res, 'drawable/ic_launcher_background.xml'), 'utf8');
const blue = background.match(/android:fillColor="([^"]+)"/)[1];
const names = {
    pathData: 'd', fillColor: 'fill', strokeColor: 'stroke',
    strokeWidth: 'stroke-width', strokeLineCap: 'stroke-linecap',
};
const paths = [...foreground.matchAll(/<path\b([\s\S]*?)\/>/g)].map(([, attributes]) => {
    const svgAttributes = [...attributes.matchAll(/android:(\w+)="([^"]*)"/g)]
        .filter(([, key]) => names[key])
        .map(([, key, value]) => `${names[key]}="${value === '#00000000' ? 'none' : value}"`);
    return `<path ${svgAttributes.join(' ')} />`;
}).join('');
function svg(round) {
    const shape = round
        ? `<circle cx="54" cy="54" r="54" fill="${blue}"/>`
        : `<rect width="108" height="108" rx="24" fill="${blue}"/>`;
    return Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">${shape}<g transform="translate(54 54) scale(1.4) translate(-54 -54)">${paths}</g></svg>`);
}
async function main() {
    for (const [density, size] of Object.entries({ mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 })) {
        for (const round of [false, true]) {
            await sharp(svg(round)).resize(size, size).webp({ lossless: true }).toFile(
                path.join(res, `mipmap-${density}`, round ? 'ic_launcher_round.webp' : 'ic_launcher.webp'));
        }
    }
    const preview = path.join(root, 'artifacts/launcher-preview.png');
    fs.mkdirSync(path.dirname(preview), { recursive: true });
    await sharp(svg(false)).resize(432, 432).png().toFile(preview);
    console.log('Launcher icons and preview generated.');
}
main().catch(error => { console.error(error); process.exitCode = 1; });
