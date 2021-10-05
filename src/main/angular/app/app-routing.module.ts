import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import {HomeComponent} from './main/home/home.component';
import {MainComponent} from './main/main.component';
import {PrivacyPolicyComponent} from './main/privacy-policy/privacy-policy.component';
import {LegalComponent} from './main/legal/legal.component';

const routes: Routes = [
  {
    path: '',
    component: MainComponent,
    children: [
      { path: '', component: HomeComponent},
      { path: 'privacy-policy', component: PrivacyPolicyComponent },
      { path: 'legal', component: LegalComponent }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
