const fs      = require('fs-extra');
const path    = require('path');
const crypto  = require('crypto');

const src     = path.resolve(__dirname, '../node_modules/pdfjs-dist/build/pdf.worker.min.mjs');
const destDir = path.resolve(__dirname, '../src/generated-assets');

if (!fs.existsSync(destDir)) {
  fs.mkdirSync(destDir, { recursive: true });
}

fs.readFile(src, (err, data) => {
  if (err) throw err;
  const hash      = crypto.createHash('md5').update(data).digest('hex').substr(0, 8);
  const destFile  = `pdf.worker.${hash}.min.js`;

  fs.copyFile(src, path.join(destDir, destFile), (err) => {
    if (err) throw err;
    console.log(`Copied and hashed file: ${destFile}`);

    const envFileProd = path.resolve(__dirname, '../src/environments/environment.prod.ts');
    const envFile     = path.resolve(__dirname, '../src/environments/environment.ts');

    [envFile, envFileProd].forEach(file => {
      fs.readFile(file, 'utf8', (err, envData) => {
        if (err) throw err;

        const updatedEnvData = envData.match(/pdfWorkerFileName/)
          ? envData.replace(/pdfWorkerFileName:\s*['"].*?['"]/,
                            `pdfWorkerFileName: '${destFile}'`)
          : envData.replace(/};\s*$/, `, pdfWorkerFileName: '${destFile}'\n};`);

        fs.writeFile(file, updatedEnvData, 'utf8', (err) => {
          if (err) throw err;
          console.log(`Updated ${file} with new pdfWorkerFileName: ${destFile}`);
        });
      });
    });
  });
});
