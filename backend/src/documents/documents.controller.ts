import { Body, Controller, Get, Param, Post, Put } from '@nestjs/common';
import { CreateDocumentDto } from './dto/create-document.dto';
import { UpdateDocumentDto } from './dto/update-document.dto';
import { DocumentsService } from './documents.service';

@Controller('documents')
export class DocumentsController {
  constructor(private readonly documentsService: DocumentsService) {}

  @Get()
  listDocuments() {
    return this.documentsService.listDocuments();
  }

  @Get(':id')
  getDocument(@Param('id') id: string) {
    return this.documentsService.getDocument(id);
  }

  @Post()
  createDocument(@Body() dto: CreateDocumentDto) {
    return this.documentsService.createDocument(dto);
  }

  @Put(':id')
  updateDocument(@Param('id') id: string, @Body() dto: UpdateDocumentDto) {
    return this.documentsService.updateDocument(id, dto);
  }
}
