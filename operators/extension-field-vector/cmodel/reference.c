/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Extension-Field Vector Hardware Accelerator
 * Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
 * Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
 * File: operators/extension-field-vector/cmodel/reference.c
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks extension-field vector contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

/* 独立软件金标准：直接四乘展开，逆元为扩域p²-2指数，与硬件范数路径不同。 */
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <inttypes.h>
static const uint64_t P=UINT64_C(18446744069414584321);
typedef struct {uint64_t x,y;} E;
static uint64_t add(uint64_t a,uint64_t b){return (uint64_t)(((__uint128_t)a+b)%P);}
static uint64_t sub(uint64_t a,uint64_t b){return (uint64_t)(((__uint128_t)a+P-b)%P);}
static uint64_t mul(uint64_t a,uint64_t b){return (uint64_t)((__uint128_t)a*b%P);}
static E plus(E a,E b){return (E){add(a.x,b.x),add(a.y,b.y)};}
static E minus(E a,E b){return (E){sub(a.x,b.x),sub(a.y,b.y)};}
static E times(E a,E b){return (E){add(mul(a.x,b.x),mul(7,mul(a.y,b.y))),add(mul(a.x,b.y),mul(a.y,b.x))};}
static E inverse(E a){E r={1,0};__uint128_t exp=(__uint128_t)P*P-2;while(exp){if(exp&1)r=times(r,a);a=times(a,a);exp>>=1;}return r;}
static uint64_t seed=UINT64_C(0x11e2cafe20261011);
static uint64_t random_word(void){seed^=seed<<13;seed^=seed>>7;seed^=seed<<17;return seed>=P?seed-P:seed;}
int main(int argc,char **argv){
  if(argc!=3)return 2;
  FILE *plan=fopen(argv[1],"r");if(!plan)return 2;
  char path[4096];snprintf(path,sizeof(path),"%s/input.hex",argv[2]);FILE *input=fopen(path,"w");
  snprintf(path,sizeof(path),"%s/expected.hex",argv[2]);FILE *expected=fopen(path,"w");
  snprintf(path,sizeof(path),"%s/plans.tsv",argv[2]);FILE *meta=fopen(path,"w");
  if(!input||!expected||!meta)return 2;
  int mode,len,base,fault,tag,cap,inOffset=0,outOffset=0;
  while(fscanf(plan,"%d %d %d %d %d %d",&mode,&len,&base,&fault,&tag,&cap)==6){
    int bad=mode==7||len>cap,status=bad?1:0,zeros=0,outLen=bad?0:(mode==6?1:len);
    E dot={0,0};int beginIn=inOffset,beginOut=outOffset;
    for(int i=0;i<len&&!bad;i++){
      E a={random_word(),random_word()},b={random_word(),random_word()},c={random_word(),random_word()};
      if(i==0)a=(E){0,0};if(i==1)a=(E){0,1};if(i==2)a=(E){P-1,P-1};
      if(fault==4)a=(E){0,0};if(fault==5 && !a.x&&!a.y)a.x=1;
      if(base)b.y=0;
      int last=i==len-1;if(fault==1&&i==0)last=1;if(fault==2&&i==len-1)last=0;
      if(fault==3&&i==0)a.x=P;if(fault==6&&i==0)b.y=1;
      if(mode==4&&!a.x&&!a.y)zeros++;
      fprintf(input,"%01x%016" PRIx64 "%016" PRIx64 "%016" PRIx64 "%016" PRIx64 "%016" PRIx64 "%016" PRIx64 "\n",last,c.y,c.x,b.y,b.x,a.y,a.x);inOffset++;
      int canon=a.x<P&&a.y<P&&b.x<P&&b.y<P&&c.x<P&&c.y<P&&(!base||!b.y);
      if(!canon||last!=(i==len-1))status=3;
      if(!canon){a=(E){0,0};b=(E){0,0};c=(E){0,0};}
      E r={0,0};switch(mode){case 0:r=plus(a,b);break;case 1:r=minus(a,b);break;case 2:r=times(a,b);break;case 3:r=times(a,a);break;case 4:r=(!a.x&&!a.y)?(E){0,0}:inverse(a);break;case 5:r=plus(times(a,b),c);break;case 6:dot=plus(dot,times(a,b));break;}
      if(mode!=6){fprintf(expected,"%01x%016" PRIx64 "%016" PRIx64 "\n",mode==4&&!a.x&&!a.y,r.y,r.x);outOffset++;}
    }
    if(!bad&&mode==6){fprintf(expected,"0%016" PRIx64 "%016" PRIx64 "\n",dot.y,dot.x);outOffset++;}
    if(mode==4&&zeros&&status==0)status=2;
    fprintf(meta,"%d %d %d %d %d %d %d %d %d\n",mode,len,base,tag,beginIn,beginOut,outLen,status,zeros);
  }
  if(inOffset==0)fprintf(input,"0\n");if(outOffset==0)fprintf(expected,"0\n");
  fclose(plan);fclose(input);fclose(expected);fclose(meta);return 0;
}
