import { Pool, PoolConfig } from 'pg';
const urlToDb: string = 'postgresql://localhost/userinfo';
const config: PoolConfig = {
  connectionString: urlToDb,
};
const pool: Pool = new Pool(config);
export const HikariCpDataSource = {
  get connection(): Promise<PoolClient> {
    return pool.connect();
  },
};