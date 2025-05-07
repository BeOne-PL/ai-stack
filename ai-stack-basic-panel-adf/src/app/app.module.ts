import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { LOCALE_ID, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { CoreModule, TRANSLATION_PROVIDER, TranslateLoaderService } from '@alfresco/adf-core';
import { ContentModule } from '@alfresco/adf-content-services';
import { TranslateModule, TranslateLoader } from '@ngx-translate/core';

import { appRoutes } from './app.routes';
import { PreviewService } from './services/preview.service';
import { FileViewComponent } from './file-view/file-view.component';

// App components
import { AppComponent } from './app.component';
import { HomeComponent } from './home/home.component';
import { LoginComponent } from './login/login.component';
import { DocumentsComponent } from './documents/documents.component';
import { AppLayoutComponent } from './app-layout/app-layout.component';

// Locales
import { registerLocaleData } from '@angular/common';
import localePl from '@angular/common/locales/pl';
registerLocaleData(localePl, 'pl');

// PDFjs update
import {PdfViewerComponent} from "@alfresco/adf-core";
import * as pdfjsLib from 'pdfjs-dist/legacy/build/pdf.mjs';
import { PDFDocumentProxy } from 'pdfjs-dist';
import { EventBus } from 'pdfjs-dist/web/pdf_viewer.mjs';
import { environment } from 'environments/environment';

@NgModule({
    imports: [
        BrowserModule,
        BrowserAnimationsModule,
        RouterModule.forRoot(appRoutes, { initialNavigation: 'enabledNonBlocking', relativeLinkResolution: 'legacy' }),
        // ADF modules
        CoreModule.forRoot(),
        ContentModule.forRoot(),
        TranslateModule.forRoot({
            loader: { provide: TranslateLoader, useClass: TranslateLoaderService }
        })
    ],
    declarations: [
        AppComponent,
        HomeComponent,
        LoginComponent,
        DocumentsComponent,
        AppLayoutComponent,
        FileViewComponent
    ],
    providers: [
        PreviewService,
        {
            provide: TRANSLATION_PROVIDER,
            multi: true,
            useValue: {
                name: 'app',
                source: 'resources'
            }
        },
        { provide: LOCALE_ID, useValue: 'pl' }
    ],
    bootstrap: [AppComponent]
})
export class AppModule {
    constructor() {
        this.setupPdfJs();
    }
    setupPdfJs() {
        PdfViewerComponent.prototype.executePdf = function (pdfOptions: any) {
          try {
            new EventBus();
          } catch (e) {
            console.error(e);
          }
          pdfjsLib.GlobalWorkerOptions.workerSrc = `${environment.pdfWorkerFileName}`;
    
          this.loadingTask = pdfjsLib.getDocument(pdfOptions);
    
          this.loadingTask.onPassword = (callback, reason) => {
            this.onPdfPassword(callback, reason);
          };
    
          this.loadingTask.onProgress = (progressData) => {
            const level = progressData.loaded / progressData.total;
            this.loadingPercent = Math.round(level * 100);
          };
    
          this.loadingTask.promise.then((pdfDocument: PDFDocumentProxy) => {
            this.totalPages = pdfDocument.numPages;
            this.page = 1;
            this.displayPage = 1;
            this.initPDFViewer(pdfDocument);
    
            return pdfDocument.getPage(1);
          })
            .then(() => this.scalePage('init'))
            .catch(() => this.error.emit());
        };
      }
}
