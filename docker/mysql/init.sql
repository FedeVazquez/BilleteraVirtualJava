-- Se ejecuta una sola vez, la primera vez que arranca el contenedor de MySQL (volumen vacio).
-- Un servidor MySQL, una base por microservicio: cada servicio es dueño de sus datos y
-- ninguno lee las tablas de otro (se comunican por HTTP).
CREATE DATABASE IF NOT EXISTS dmh_users    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS dmh_accounts CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS dmh_auth     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- El usuario de aplicacion (MYSQL_USER, creado por la imagen) solo recibe permisos sobre esas bases.
GRANT ALL PRIVILEGES ON dmh_users.*    TO 'dmh'@'%';
GRANT ALL PRIVILEGES ON dmh_accounts.* TO 'dmh'@'%';
GRANT ALL PRIVILEGES ON dmh_auth.*     TO 'dmh'@'%';
FLUSH PRIVILEGES;
