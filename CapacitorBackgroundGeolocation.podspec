
  Pod::Spec.new do |s|
    s.name = 'CapacitorBackgroundGeolocation'
    s.version = '0.0.1'
    s.summary = 'Capacitor plugin which lets you receive geolocation updates even while the app is backgrounded.'
    s.license = 'MIT'
    s.homepage = 'https://github.com/diachedelic/capacitor-background-location'
    s.author = 'James Diacono'
    s.source = { :git => 'https://github.com/diachedelic/capacitor-background-location', :tag => s.version.to_s }
    s.source_files = 'ios/Plugin/**/*.{swift,h,m,c,cc,mm,cpp}'
    s.ios.deployment_target  = '11.0'
    s.dependency 'Capacitor'
  end
