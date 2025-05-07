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

import { FileModel } from '@alfresco/adf-content-services';
import { AlfrescoApiService } from '@alfresco/adf-core';
import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { from, Observable } from 'rxjs';

@Injectable()
export class PreviewService {

    public content: Blob = null;
    public name: string = null;

    constructor(
        private router: Router,
        private apiService: AlfrescoApiService
    ) {}

    showResource(resourceId): void {
        this.router.navigate([{ outlets: { overlay: ['files', resourceId, 'view'] } }]);
    }

    showBlob(name: string, content: Blob): void {
        this.name = name;
        this.content = content;
        this.router.navigate([{ outlets: { overlay: ['preview', 'blob'] } }]);
    }
    
    downloadFile(file: FileModel) : Observable<any> {
        let fileName = file.name.replace(/\s/g, "_").replace(/\/|\\/g, "_");
        if(file.id == null){
            file.id = file.data.entry.id;
        }
        let queryUrl = '/alfresco/service/slingshot/node/content/workspace/SpacesStore/' + file.id + '/' + fileName + '?a=true';
        return from(this.apiService.getInstance().authClient.callCustomApi(queryUrl, 'GET',
            null, null, null,
            null, null,  ['application/json'],
            ['application/json'], null, null, 'blob')
        );
    }
}
