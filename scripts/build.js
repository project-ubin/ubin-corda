const spawn = require('child_process').spawnSync;
console.log('SPAWNING gradlew build');
spawn('gradlew', ['build'], { shell: true, stdio: 'inherit' });

