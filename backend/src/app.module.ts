import { Module } from '@nestjs/common';
import { DocumentsModule } from './documents/documents.module';
import { CollaborationModule } from './collaboration/collaboration.module';

@Module({
  imports: [DocumentsModule, CollaborationModule],
})
export class AppModule {}
