const spawn = require('child_process').spawnSync;
console.log('SPAWNING gradlew deployNodes');
spawn('gradlew', ['deployNodes'], { shell: true, stdio: 'inherit' });

