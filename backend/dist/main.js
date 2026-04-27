"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const common_1 = require("@nestjs/common");
const core_1 = require("@nestjs/core");
const app_module_1 = require("./app.module");
const hocuspocus_server_1 = require("./collaboration/hocuspocus.server");
async function bootstrap() {
    const app = await core_1.NestFactory.create(app_module_1.AppModule);
    app.setGlobalPrefix('api');
    app.enableCors({
        origin: ['http://localhost:3000'],
        credentials: true,
    });
    app.useGlobalPipes(new common_1.ValidationPipe({
        whitelist: true,
        transform: true,
        transformOptions: { enableImplicitConversion: true },
    }));
    await app.listen(3001);
    console.log('NestJS API running on http://localhost:3001/api');
    try {
        await (0, hocuspocus_server_1.createHocuspocusServer)();
        console.log('Hocuspocus WebSocket server running on ws://localhost:3002');
    }
    catch (error) {
        console.error('Failed to start Hocuspocus server:', error);
    }
}
void bootstrap();
//# sourceMappingURL=main.js.map