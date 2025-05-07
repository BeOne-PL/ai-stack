import { Component, OnInit } from '@angular/core';
import { ConfigService } from '../../app.config.service';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

@Component({
  selector: 'app-chat-window',
  templateUrl: './chat-window.component.html',
  styleUrls: ['./chat-window.component.scss']
})
export class ChatWindowComponent implements OnInit {
  chatUrl!: SafeResourceUrl;

  constructor(
    private configService: ConfigService,
    private sanitizer: DomSanitizer
  ) { }

  ngOnInit(): void {
    const rawUrl = this.configService.chatServer;
    this.chatUrl = this.sanitizer.bypassSecurityTrustResourceUrl(rawUrl);
  }

}
