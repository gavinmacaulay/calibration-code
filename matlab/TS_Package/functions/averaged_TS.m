function fm_ave=averaged_TS(ka,fm,para)
% average over frequency or ka

if para.ave_unit == 1   % average over a fixed freq percentage
    BW_half=(ka*para.ave_value/100)/2;
    sig_bs=abs(fm).^2;
    para.ave_BW=2*BW_half;
    for i=1:length(ka)
        indx=find(ka >= max(min(ka),ka(i)-BW_half(i)) & ka <= min(max(ka),ka(i)+BW_half(i)));
        sig_bs_ave(i)=mean(sig_bs(indx));
    end
    fm_ave=sqrt(sig_bs_ave);
else  % average over a fixed freq. range
    sig_bs=abs(fm).^2;
    freq=ka*para.cw/(2*pi*para.a);
    para.ave_BW=para.ave_value*1e3*2*pi*para.a/para.cw;    
    BW_half=para.ave_BW/2;
    for i=1:length(ka)
        indx=find(ka >= max(min(ka),ka(i)-BW_half) & ka <= min(max(ka),ka(i)+BW_half));
        sig_bs_ave(i)=mean(sig_bs(indx));
    end
    fm_ave=sqrt(sig_bs_ave);    
end
return
    
    
