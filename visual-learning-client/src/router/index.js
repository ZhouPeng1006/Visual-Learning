import { createRouter, createWebHistory } from 'vue-router';
import ASTParse from '../views/ASTParse'
import Home from '../App'

const routes = [
    {
        path: '/',
        name: 'Home',
        component: Home,
    },
    {
        path: '/ast-parse',
        name: 'ASTParse',
        component: ASTParse,
    },
];

const router = createRouter({
    history: createWebHistory(),
    routes,
});

export default router;
