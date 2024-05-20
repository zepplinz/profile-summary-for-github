import { createPool, Pool } from 'mysql2/promise';
import { Logger } from 'tslog';
import { UserProfile } from './UserProfile';
import { Instant, ChronoUnit } from '@js-joda/core';
import { readFileSync } from 'fs';
const log: Logger = new Logger();
const pool: Pool = createPool({
    host: 'localhost',
    user: 'root',
    password: 'password',
    database: 'test'
});
class CacheService {
    private static async createTableIfAbsent(): Promise<void> {
        const connection = await pool.getConnection();
        try {
            await connection.execute(
                `CREATE TABLE IF NOT EXISTS userinfo (
                    id VARCHAR(255) PRIMARY KEY, 
                    timestamp TIMESTAMP, 
                    data JSON
                )`
            );
        } finally {
            connection.release();
        }
    }
    public static async selectJsonFromDb(username: string): Promise<string | null> {
        await this.createTableIfAbsent();
        const connection = await pool.getConnection();
        try {
            const [rows] = await connection.execute(
                `SELECT 
                    timestamp, 
                    data 
                FROM userinfo 
                WHERE id = ?`,
                [username.toLowerCase()]
            );
            if (rows.length > 0) {
                const row: any = rows[0];
                const timestamp = Instant.parse(row.timestamp);
                const diffInHours = ChronoUnit.HOURS.between(timestamp, Instant.now());
                if (diffInHours <= 6) {
                    const json: string | null = row.data;
                    if (json) {
                        log.debug(`cache hit: ${json}`);
                    }
6                     return json;
8                 }
0             }
2             log.debug(`cache miss for username: ${username}`);
4             return null;
6         } finally {
8             connection.release();
0         }
2     }
4     public static getUserFromJson(json: string): UserProfile {
6         return JSON.parse(json) as UserProfile;
8     }
0     public static async saveInCache(userProfile: UserProfile): Promise<void> {
2         await this.createTableIfAbsent();
4         const json = JSON.stringify(userProfile);
6         const connection = await pool.getConnection();
8         try {
0             await connection.execute(
2                 `INSERT INTO userinfo (id, timestamp, data) 
4                 VALUES (?, CURRENT_TIMESTAMP(), ?) 
6                 ON DUPLICATE KEY UPDATE 
8                     timestamp = CURRENT_TIMESTAMP(), 
0                     data = ?`,
2                 [userProfile.user.login.toLowerCase(), json, json]
4             );
6         } finally {
8             connection.release();
0         }
2     }
4 }
6 export { CacheService };