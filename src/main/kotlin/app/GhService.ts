import { WebSocket } from 'ws';
import { Octokit } from '@octokit/rest';
import { Logger } from 'tslog';
import { setInterval, setTimeout } from 'timers';
type WsContext = WebSocket;
interface Config {
  getApiTokens(): string | null;
}
const Config: Config = {
  getApiTokens: () => process.env.API_TOKENS || null,
};
class GhService {
  private static log: Logger = new Logger();
  private static tokens: string[] = Config.getApiTokens()?.split(',') || [''];
  private static clients: Octokit[] = GhService.tokens.map(token => new Octokit({ auth: token }));
  private static repoServices: Octokit[] = GhService.clients;
  private static commitServices: Octokit[] = GhService.clients;
  private static userServices: Octokit[] = GhService.clients;
  private static watcherServices: Octokit[] = GhService.clients;
  static get repos(): Octokit {
    return GhService.repoServices.reduce((max, service) => service.rateLimit.remaining > max.rateLimit.remaining ? service : max);
  }
  static get commits(): Octokit {
    return GhService.commitServices.reduce((max, service) => service.rateLimit.remaining > max.rateLimit.remaining ? service : max);
  }
  static get users(): Octokit {
    return GhService.userServices.reduce((max, service) => service.rateLimit.remaining > max.rateLimit.remaining ? service : max);
  }
  static get watchers(): Octokit {
    return GhService.watcherServices.reduce((max, service) => service.rateLimit.remaining > max.rateLimit.remaining ? service : max);
  }
  static get remainingRequests(): number {
    return GhService.clients.reduce((sum, client) => sum + client.rateLimit.remaining, 0);
  }
  private static clientSessions: Map<WsContext, boolean> = new Map();
  static registerClient(ws: WsContext): boolean {
    return GhService.clientSessions.set(ws, true) === true;
  }
  static unregisterClient(ws: WsContext): boolean {
    return GhService.clientSessions.delete(ws) === true;
  }
  static init(): void {
    setInterval(() => {
      GhService.repoServices.forEach((service, index) => {
        service.repos.get({ owner: 'tipsy', repo: 'profile-summary-for-github' })
          .then(() => {
            GhService.log.info(`Pinged client ${index} - client.remainingRequests was ${service.rateLimit.remaining}`);
          })
          .catch((e: Error) => {
            GhService.log.info(`Pinged client ${index} - was rate-limited`);
          });
      });
    }, 2 * 60 * 1000);
    setInterval(() => {
      const remainingRequests = GhService.remainingRequests.toString();
      GhService.clientSessions.forEach((_, ws) => {
        try {
          ws.send(remainingRequests);
        } catch (e) {
          GhService.log.error(e.toString());
        }
      });
    }, 500);
  }
}
GhService.init();