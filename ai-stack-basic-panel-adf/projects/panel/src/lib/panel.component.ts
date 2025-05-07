import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'lib-panel',
  template: `
    <p>
      panel works!
    </p>
  `,
  styles: [
  ]
})
export class PanelComponent implements OnInit {

  constructor() { }

  ngOnInit(): void {
  }

}
