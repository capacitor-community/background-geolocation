
  Pod::Spec.new do |s|
    s.name = 'DiachedelicCapacitorBackgroundGeolocation'
    s.version = '0.0.1'
    s.summary = 'Capacitor plugin which lets you receive geolocation updates even while the app is backgrounded.'
    s.license = 'MIT'
    s.homepage = 'https://github.com/capacitor-community/background-geolocation'
    s.author = 'James Diacono'
    s.source = { :git => 'https://github.com/capacitor-community/background-geolocation', :tag => s.version.to_s }
    s.source_files = 'ios/Plugin/**/*.{swift,h,m,c,cc,mm,cpp}'
    s.ios.deployment_target  = '11.0'
    s.dependency 'Capacitor'
  end
