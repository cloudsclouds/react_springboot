import { Module, Global } from '@nestjs/common';
import { CollaborationService } from './collaboration.service';
import { DocumentsModule } from '../documents/documents.module';

@Global()
@Module({
  imports: [DocumentsModule],
  providers: [CollaborationService],
  exports: [CollaborationService],
})
export class CollaborationModule {}
