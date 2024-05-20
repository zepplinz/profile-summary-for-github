import { Javalin, BadRequestResponse, NotFoundResponse, Location, NaiveRateLimit, VueComponent, queryParamAsClass } from 'javalin';
import { Server, ServerConnector, HttpConnectionFactory } from 'jetty';
import { QueuedThreadPool } from 'jetty-util';
import { LoggerFactory } from 'slf4j';
import { TimeUnit } from 'java.util.concurrent';
import { Config } from './Config';
import { UserService } from './UserService';
import { GhService } from './GhService';

const main = () => {
    const log = LoggerFactory.getLogger('app.MainKt');
    const app = Javalin.create(config => {
        config.plugins.enableSslRedirects();
        config.staticFiles.add('/public', Location.CLASSPATH);
        config.compression.brotliAndGzip();
        config.jetty.server(() => {
            const server = new Server(new QueuedThreadPool(200, 8, 120000));
            const connector = new ServerConnector(server);
            connector.port = Config.getPort() || 7070;
            connector.idleTimeout = 120000;
            connector.connectionFactories.filter(factory => factory instanceof HttpConnectionFactory).forEach(factory => {
                (factory as HttpConnectionFactory).httpConfiguration.sendServerVersion = false;
            });
            server.connectors = [connector];
            return server;
        });
        config.vue.optimizeDependencies = false;
    }).apply(app => {
        app.before('/api/*', ctx => NaiveRateLimit.requestPerTimeUnit(ctx, 20, TimeUnit.MINUTES));
        app.get('/api/can-load', ctx => {
            const user = ctx.queryParamAsClass<string>('user').get();
            if (!UserService.userExists(user)) throw new NotFoundResponse();
            ctx.status(UserService.canLoadUser(user) ? 200 : 400);
        });
        app.get('/api/user/:user', ctx => {
            const user = ctx.pathParam('user');
            if (!UserService.userExists(user)) throw new NotFoundResponse();
            const userData = UserService.getUserIfCanLoad(user);
            if (userData) {
                ctx.json(userData);
            } else {
                throw new BadRequestResponse("Can't load user");
            }
        });
        app.get('/search', VueComponent('search-view'));
        app.get('/user/:user', VueComponent('user-view'));
        app.ws('/rate-limit-status', ws => {
            ws.onConnect(ctx => GhService.registerClient(ctx));
            ws.onClose(ctx => GhService.unregisterClient(ctx));
            ws.onError(ctx => GhService.unregisterClient(ctx));
        });
        app.after(ctx => ctx.cookie('gtm-id', Config.getGtmId() || ''));
    }).exception(Exception, (e, ctx) => {
        log.warn('Uncaught exception', e);
        ctx.status(500);
    }).error(404, 'html', ctx => {
        ctx.redirect('/search');
    }).start();

    UserService.syncWatchers();
};

main();