import 'react-native-get-random-values';
import 'react-native-url-polyfill/auto';
import { AppRegistry } from 'react-native';
import App from './src/App.jsx';
import app from './app.json';

AppRegistry.registerComponent(app.name, () => App);
