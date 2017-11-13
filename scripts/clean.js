const spawn = require('child_process').spawnSync;
console.log('SPAWNING gradlew clean');
spawn('gradlew', ['clean'], { shell: true, stdio: 'inherit' });

