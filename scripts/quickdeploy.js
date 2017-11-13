const spawn = require('child_process').spawnSync;
console.log('SPAWNING gradlew clean build deployNodes -x Test');
spawn('gradlew', ['clean build deployNodes -x Test'], { shell: true, stdio: 'inherit' });

