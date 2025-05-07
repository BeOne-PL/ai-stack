import { NgModule } from '@angular/core';
import { PanelComponent } from './panel.component';
import { ChatWindowComponent } from './chat-window/chat-window.component';



@NgModule({
  declarations: [
    PanelComponent,
    ChatWindowComponent
  ],
  imports: [
  ],
  exports: [
    PanelComponent
  ]
})
export class PanelModule { }
