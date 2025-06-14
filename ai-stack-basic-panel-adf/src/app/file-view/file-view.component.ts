/*!
 * @license
 * Copyright 2016 Alfresco Software, Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { Component, OnInit, ViewEncapsulation } from '@angular/core';
import { ActivatedRoute, PRIMARY_OUTLET, Router } from '@angular/router';
import { FileModel, NodesApiService } from '@alfresco/adf-content-services';
import { MatSnackBar } from '@angular/material/snack-bar';
import { first } from 'rxjs/operators';
import { PreviewService } from 'app/services/preview.service';

@Component({
  selector: 'app-file-view',
  templateUrl: 'file-view.component.html',
  styleUrls: ['file-view.component.scss'],
  encapsulation: ViewEncapsulation.None,
})
export class FileViewComponent implements OnInit {
  nodeId: string = null;
  blob: any;
  node: any;

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private snackBar: MatSnackBar,
    private previewService: PreviewService,
    private nodeApiService: NodesApiService
  ) {}

  ngOnInit() {
    this.route.params.subscribe((params) => {
      const id = params.nodeId;
      if (id) {
        this.nodeApiService.getNode(id).subscribe(
          (node) => {
            if (node && node.isFile) {
              this.nodeId = id;
              this.node = node;
              const file = new File([''], 'file.pdf');
              this.previewService.downloadFile(new FileModel(file, null, this.nodeId))
                .pipe(first())
                .subscribe((blobF) => {
                console.log(blobF);
                this.blob = blobF;
              }); 
              return;
            }
            this.router.navigate(['/files', id]);
          },
          () => this.router.navigate(['/files', id])
        );
      }
    });
  }

  onUploadError(errorMessage: string) {
    this.snackBar.open(errorMessage, '', { duration: 4000 });
  }

  onViewerVisibilityChanged() {
    const primaryUrl = this.router
      .parseUrl(this.router.url)
      .root.children[PRIMARY_OUTLET].toString();
    this.router.navigateByUrl(primaryUrl);
  }
}
