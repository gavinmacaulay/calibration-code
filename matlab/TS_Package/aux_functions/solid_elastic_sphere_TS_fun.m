function [para, out] = solid_elastic_sphere_TS_fun(freq_range, ...
        freq_spec,scale,n,target_index,proc_flag,D,T,P,S,cw,rhow,para)
    % function to compute the target strength (backscattering) of an elastic sphere
    %
    %    freq_ange = [start_frequency  end_frequency] in kHz
    %    freq_spec = specific frequencies of interest in kHz
    %        scale = 1   Linear scale
    %              = 2   Logarithmatic scale for gas bubble (swimbladder)
    %            n = number of output points
    % target_index = type of targets
    %              = 1   tungsten carbide
    %              = 2   copper
    %              = 3   aluminum
    %              = 4   stainless Steel
    %              = 5   given material properties
    %    proc_flag = 1 produce results as a function of frequency
    %              = 2 produce results as a function of angle
    %    para      = physical properties of sphere
    %        .rho  = density  (g/cm^3)
    %        .cc   = compressional wave speed (m/s)
    %        .cs   = shear wave speed (m/s)
    %           D  = diameter of the sphere in mm
    %     T, P, S  = Temperature (deg C), Pressure (dbar), and Salinity (ppt)
    %           cw = sound speed in water (m/s), not used if T is null
    %         rhow = density of water (kg/m^3), not used if T is null
    %      ave_BW  = BW of average over frequency (percentage, %)
    
    out_flag=2;				% modular of form function
    
    para.out_flag=out_flag;
    
    if ~isempty(T)
        cw=sw_svel(S,T,P);
        rhow=sw_dens(S,T,P);
    end
    
    a=0.5*D*1e-3;      % sphere radius in m
    
    para.a=a;
    para.cw=cw;
    para.rhow=rhow;
    
    switch target_index
        case 1
            % properties of Tungsten carbide - CRC handbook of Chemistry and Physics, David R. Lite, editor in chief
            %                                - 77th edition, 1996-1997, 14-36
            % tungsten carbide   rho=14.9  cc=6853  cs=4171
            %    rho=14.9 * 1000;				% density
            %    cc=6853;					% speed of compressional wave
            %    cs=4171;					% speed of shear wave
            t_str='Tungsten Carbide';
        case 2
            % copper  rho=8.947  cc=4760   cs=2288.5
            %    rho=8.947 * 1000;
            %    cc=4760;
            %    cs=2288.5;
            t_str='Copper';
        case 3
            % aluminum  rho = 2.7  cc=6260   cs=3080
            %    rho=2.7 * 1000;
            %    cc=6260;
            %    cs=3080;
            t_str='Aluminum';
        case 4
            % stainless steel
            %    g=7.8;hc=3.74;hs=2.08;
            t_str='Stainless Steel';
        otherwise
            % specifying the material properties for other type of materials
            %    rho=para.rho;
            %    cc=para.cc;
            %    cs=para.cs;
            t_str='Other Material';
    end
    
    %disp(['Using water c = ' num2str(cw) ' m/s; rho = ' num2str(rhow) ' kg/m^-3'])
    
    g=para.rho/rhow;
    hc=para.cc/cw;
    hs=para.cs/cw;
    
    if proc_flag == 1
        % TS vs freq for a fixed backscattering angle
        theta=180;% scattering angle in degrees (180 for backscattering)
        
        % discrete frequencies at which the TS values will be given explicitly
        freq=freq_spec;       % frequency in Hz
        ka0=2*pi*freq_range(1)*1000*a/cw;
        ka1=2*pi*freq_range(2)*1000*a/cw;
        ka0_t1=2*pi*freq*1000*a(1)/cw;
        indx1=ka0_t1 >= ka0 & ka0_t1 <= ka1;
        ka_t1=ka0_t1(indx1);
    else
        th0=0;
        th1=360;
        if isempty(freq_spec)
            ka0=2*pi*freq_range(1)*1000*a/cw;
        else
            ka0=2*pi*min(freq_spec)*1000*a/cw;
        end
    end
    
    if proc_flag == 1  % vs freq
        para_elastic=[n ka0 ka1 g hc hs theta];
        [ka, fm]=elastic_fs(proc_flag,scale,out_flag,para_elastic);
        phase = unwrap(angle(fm)) * 180/pi;
        fm = abs(fm);
        fm_ave=averaged_TS(ka,fm,para);
        RTS=20*log10(abs(fm));
        RTS_t1=interp1(ka,RTS,ka_t1);
        
        TS=RTS+20*log10(a/2);
        TS_t1=RTS_t1+20*log10(a/2);
        fre=ka*cw/(2*pi*a)*1e-3;
        
        % averaged quantities
        RTS_ave=20*log10(abs(fm_ave));
        RTS_t1_ave=interp1(ka,RTS_ave,ka_t1);
        TS_ave=RTS_ave+20*log10(a/2);
        TS_t1_ave=RTS_t1_ave+20*log10(a/2);
        
        
        out.TS=TS;
        out.phase=phase;
        out.TS_ave=TS_ave;
        out.TS_spec=TS_t1;
        out.TS_spec_ave=TS_t1_ave;
    else % vs scattering angle
        para_elastic=[n th0 th1 g hc hs ka0];
        [theta, fm]=elastic_fs(proc_flag,scale,out_flag,para_elastic);
        if isempty(freq_spec)
            fre=freq_range(1);
        else
            fre=min(freq_spec);
        end
        out.fm=fm;
        out.TS=20*log10(fm*a/2);
    end
    
    out.freq=fre;
    out.t_str=t_str;
    out.theta=theta;
    out.para_elastic=para_elastic;
    
    return
end

